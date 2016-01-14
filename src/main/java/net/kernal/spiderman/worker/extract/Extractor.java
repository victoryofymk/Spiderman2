package net.kernal.spiderman.worker.extract;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.kernal.spiderman.Properties;
import net.kernal.spiderman.worker.extract.conf.Field;
import net.kernal.spiderman.worker.extract.conf.Model;

/**
 * 页面提取器
 * @author 赖伟威 l.weiwei@163.com 2016-01-08
 *
 */
public abstract class Extractor {

	private ExtractTask task;
	/**
	 * 所属页面名称
	 */
	private String page;
	private List<Model> models;
	
	protected Extractor(ExtractTask task, String page, Model... models) {
		this.task = task;
		this.page = page;
		this.models = new ArrayList<Model>(Arrays.asList(models));
	}
	public Extractor addModel(Model model) {
		this.models.add(model);
		return this;
	}
	public ExtractTask getTask() {
		return this.task;
	}
	public String getPage() {
		return this.page;
	}
	protected List<Model> getModels() {
		return this.models;
	}
	
	public abstract void extract(Callback callback);
	
	public static interface Builder {
		public Extractor build(ExtractTask task, String page, Model... models);
	}
	
	public static interface Callback{
		
		public void onModelExtracted(ModelEntry entry);
		public void onFieldExtracted(FieldEntry entry);
		
		public static class ModelEntry {
			private Model model;
			private Properties properties;
			public ModelEntry(Model model, Properties properties) {
				this.model = model;
				this.properties = properties;
			}
			public Model getModel() {
				return this.model;
			}
			public Properties getProperties() {
				return this.properties;
			}
		}
		public static class FieldEntry {
			private Field field;
			private List<Object> values;
			private Object data;
			public FieldEntry(Field field, List<Object> values) {
				this.field = field;
				this.values = values;
			}
			public Field getField() {
				return this.field;
			}
			public List<Object> getValues() {
				return this.values;
			}
			public void setData(Object data) {
				this.data = data;
			}
			public Object getData() {
				return this.data;
			}
		}
	}
	
}