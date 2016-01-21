package net.kernal.spiderman.queue;

/**
 * 可检查队列，元素入队列的时候会受到检查器检查，只有检查通过的元素方可入队列
 * @author 赖伟威 l.weiwei@163.com 2016-01-19
 *
 */
public class CheckableQueue implements Queue {

	private Queue queue;
	private Checker checker;
	
	public CheckableQueue(Queue queue, Checker checker) {
		if (checker == null) {
			throw new NullPointerException("Checker");
		}
		if (queue == null) {
			throw new NullPointerException("Queue");
		}
		this.queue = queue;
		this.checker = checker;
	}
	
	public void append(Element e) {
		if (checker.check(e)) {
			queue.append(e);
		}
	}
	
	public Checker getChecker() {
		return this.checker;
	}
	
	public Element take() {
		return this.queue.take();
	}

	public void clear() {
		this.queue.clear();
		this.checker.clear();
	}
	
	public static interface Checker {
		public boolean check(Element e);
		public void clear();
	}
	
}