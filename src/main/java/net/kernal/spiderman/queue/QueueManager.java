package net.kernal.spiderman.queue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.zbus.broker.Broker;
import org.zbus.broker.BrokerConfig;
import org.zbus.broker.SingleBroker;

import net.kernal.spiderman.K;
import net.kernal.spiderman.Properties;
import net.kernal.spiderman.Spiderman;
import net.kernal.spiderman.logger.Logger;
import net.kernal.spiderman.queue.CheckableQueue.Checker;
import net.kernal.spiderman.queue.Queue.Element;
import net.kernal.spiderman.store.KVStore;
import net.kernal.spiderman.worker.Task;
import net.kernal.spiderman.worker.download.DownloadTask;
import net.kernal.spiderman.worker.extract.ExtractResult;
import net.kernal.spiderman.worker.extract.ExtractTask;
import net.kernal.spiderman.worker.result.ResultTask;

public class QueueManager {
	
	private Logger logger;
	
	private Map<String, Queue<Element>> queues;
	private Queue<Task> downloadQueue;
	private Queue<Task> extractQueue;
	private Queue<Task> resultQueue;
	
	private KVStore store;
	
	public QueueManager(Properties params, Logger logger) {
		this.logger = logger;
		this.queues = new HashMap<String, Queue<Element>>();
		
		// 构建队列
		final boolean zbusEnabled = params.getBoolean("queue.zbus.enabled", false);
		if (zbusEnabled) {
			// 构建ZBus队列
			final String server = params.getString("queue.zbus.server");
			if (K.isBlank(server)) {
				throw new Spiderman.Exception("缺少参数: queue.zbus.server, 参考: conf.set(\"queue.zbus.server\", \"localhost:155555\")");
			}
			final BrokerConfig brokerConfig = new BrokerConfig();
		    brokerConfig.setServerAddress(server);
		    final Broker broker;
		    try {
		    	broker = new SingleBroker(brokerConfig);
		    } catch (Throwable e) {
		    	throw new Spiderman.Exception("连接ZBus服务失败", e);
		    }
		    final String downloadQueueName = params.getString("queue.download.name", "SPIDERMAN_DOWNLOAD_TASK");
			downloadQueue = new ZBusQueue<Task>(broker, downloadQueueName);
			logger.debug("创建下载队列(ZBus)");
			final String extractQueueName = params.getString("queue.download.name", "SPIDERMAN_EXTRACT_TASK");
			extractQueue = new ZBusQueue<Task>(broker, extractQueueName);
			logger.debug("创建解析队列(ZBus)");
			final String resultQueueName = params.getString("queue.download.name", "SPIDERMAN_RESULT_TASK");
			resultQueue = new ZBusQueue<Task>(broker, resultQueueName);
			logger.debug("创建结果队列(ZBus)");
			// 创建其他队列
			final List<String> queueNames = params.getListString("queue.other.names", "", ",");
			new HashSet<String>(queueNames).parallelStream().filter(n -> K.isNotBlank(n)).forEach(n -> {
				Queue<Element> queue = new ZBusQueue<Element>(broker, n);
				queues.put(n, queue);
				logger.debug("创建其他[name="+n+"]队列(ZBus)");
			});
		} else {
			// 队列元素是否可以重复
			final Checker checker;
			final boolean isRepeatable = params.getBoolean("queue.element.repeatable", true);
			if (!isRepeatable) {
				// 若不可重复，则需要构建检查器来实现
				checker = new RepeatableChecker(params, logger);
				logger.debug("构建队列元素重复检查器RepeatableChecker");
			} else {
				checker = null;
			}
			// 构建默认队列
			final int capacity = params.getInt("queue.capacity");
			final Queue<Task> queue1 = new DefaultQueue<Task>(capacity, logger);
			downloadQueue = checker == null ? queue1 : new CheckableQueue<Task>(queue1, checker);
			logger.debug("创建下载队列(默认)");
			final Queue<Task> queue2 = new DefaultQueue<Task>(capacity, logger);
			extractQueue = checker == null ? queue2 : new CheckableQueue<Task>(queue2, checker);
			logger.debug("创建下载队列(默认)");
			final Queue<Task> queue3 = new DefaultQueue<Task>(capacity, logger);
			resultQueue = checker == null ? queue3 : new CheckableQueue<Task>(queue3, checker);
			logger.debug("创建结果队列(默认)");
			// 创建其他队列
			final List<String> queueNames = params.getListString("queue.other.names", "", ",");
			new HashSet<String>(queueNames).parallelStream().filter(n -> K.isNotBlank(n)).forEach(n -> {
				Queue<Element> queue = new DefaultQueue<Element>(capacity, logger);
				queues.put(n, checker == null ? queue : new CheckableQueue<Element>(queue, checker));
				logger.debug("创建其他[name="+n+"]队列(默认)");
			});
		}
	}
	
	public void append(Task task) {
		final String source = task.getSource() == null ? null : task.getSource().getRequest().getUrl();
		if (task instanceof DownloadTask) {
			this.downloadQueue.append(task);
			final DownloadTask dtask = (DownloadTask)task;
			logger.info("添加下载任务: "+ dtask.getRequest().getUrl()+", 来源->"+source);
		} else if (task instanceof ExtractTask) {
			this.extractQueue.append(task);
			final ExtractTask etask = (ExtractTask)task;
			logger.info("添加解析任务: " + etask.getResponse().getRequest().getUrl()+", 来源->"+source);
		} else if (task instanceof ResultTask) {
			this.resultQueue.append(task);
			final ResultTask rtask = (ResultTask)task;
			final ExtractResult r = rtask.getResult();
			logger.info("添加结果任务: [page="+r.getPageName()+"].[model="+r.getModelName()+"], 来源->"+source);
		}
	}
	
	public Queue<Task> getDownloadQueue() {
		return this.downloadQueue;
	}
	
	public Queue<Task> getExtractQueue() {
		return this.extractQueue;
	}
	
	public Queue<Task> getResultQueue() {
		return this.resultQueue;
	}
	
	public Queue<Element> getQueue(String name) {
		return this.queues.get(name);
	}
	
	public void register(String name, Queue<Element> queue) {
		if (this.queues.containsKey(name)) {
			throw new Spiderman.Exception("duplicate name " + name);
		}
		
		this.queues.put(name, queue);
	}
	
	public void shutdown() {
		if (this.downloadQueue != null) {
			this.downloadQueue.clear();
		}
		if (this.extractQueue != null) {
			this.extractQueue.clear();
		}
		if (this.resultQueue != null) {
			this.resultQueue.clear();
		}
		if (this.queues != null && !this.queues.isEmpty()) {
			this.queues.forEach((k, q) -> q.clear());
		}
		if (this.store != null) {
			this.store.close();
		}
		
		logger.debug("退出...");
	}
	
}
