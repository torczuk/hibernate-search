package org.hibernate.search.cfg;

import java.lang.annotation.ElementType;
import java.util.HashMap;
import java.util.Map;

import org.apache.solr.analysis.TokenizerFactory;

public class IndexEmbeddedMapping {

	private final SearchMapping mapping;
	private final Map<String,Object> indexEmbedded;
	private EntityDescriptor entity;
	private PropertyDescriptor property;

	
	public IndexEmbeddedMapping(SearchMapping mapping, PropertyDescriptor property, EntityDescriptor entity) {
		this.mapping = mapping;
		this.indexEmbedded = new HashMap<String, Object>();
		this.property = property;
		this.entity = entity;
		this.property.setIndexEmbedded(indexEmbedded);
	}
	
	public IndexEmbeddedMapping prefix(String prefix) {
		this.indexEmbedded.put("prefix",prefix);
		return this;
	}
	
	public IndexEmbeddedMapping targetElement(Class<?> targetElement) {
		this.indexEmbedded.put("targetElement",targetElement);
		return this;
	}
	
	public IndexEmbeddedMapping depth(int depth) {
		this.indexEmbedded.put("depth", depth);
		return this;
	}
	
	public PropertyMapping property(String name, ElementType type) {
		return new PropertyMapping(name, type, entity, mapping);
	}

	public AnalyzerDefMapping analyzerDef(String name, Class<? extends TokenizerFactory> tokenizerFactory) {
		return new AnalyzerDefMapping(name, tokenizerFactory, mapping);
	}

	public EntityMapping entity(Class<?> entityType) {
		return new EntityMapping(entityType, mapping);
	}
	
	public FieldMapping field() {
		return new FieldMapping(property, entity, mapping);
	}

}
