package net.kernal.spiderman.worker.result;

import net.kernal.spiderman.Counter;
import net.kernal.spiderman.logger.Logger;
import net.kernal.spiderman.queue.QueueManager;
import net.kernal.spiderman.worker.Task;
import net.kernal.spiderman.worker.Worker;
import net.kernal.spiderman.worker.WorkerManager;
import net.kernal.spiderman.worker.WorkerResult;
import net.kernal.spiderman.worker.extract.ExtractManager.ResultHandler;
import net.kernal.spiderman.worker.extract.ExtractResult;

public class ResultManager extends WorkerManager {

	private ResultHandler handler;
	
	public ResultManager(int nWorkers, QueueManager queueManager, Counter counter, Logger logger, ResultHandler handler) {
		super(nWorkers, queueManager, counter, logger);
		this.handler = handler;
	}

	protected void handleResult(WorkerResult wr) {
		// 计数器加1
		final Counter counter = getCounter();
		final long count = counter.plus();
		final ResultTask rtask = (ResultTask)wr.getTask();
		final ExtractResult result = rtask.getResult();
		getLogger().info("消费了第"+count+"个结果[seed="+rtask.getSeed().getName()+",page="+result.getPageName()+", model="+result.getModelName()+", url="+rtask.getRequest().getUrl()+", source="+rtask.getSourceUrl()+"]");
		if (this.handler != null) {
			this.handler.handle(rtask, counter);
		}
	}

	protected Task takeTask() {
		return (Task)getQueueManager().getResultQueue().take();
	}

	protected Worker buildWorker() {
		return new Worker(this) {
			public void work(Task task) {
				getManager().done(new WorkerResult(null, (ResultTask)task, null));
			}
		};
	}

	protected void clear() {
	}

}
