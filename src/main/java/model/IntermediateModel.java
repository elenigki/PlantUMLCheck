package model;

import java.util.ArrayList;
import java.util.List;

public class IntermediateModel {

	private ArrayList<ClassInfo> classes; // All parsed classes/interfaces/etc.
	private ArrayList<Relationship> relationships; // All relationships between the classes
	private ModelSource modelSource;
	private List<String> warnings = new ArrayList<>();

	public IntermediateModel(ModelSource modelSource) {
		this.classes = new ArrayList<>();
		this.relationships = new ArrayList<>();
		this.modelSource = modelSource;
	}

	public void addClass(ClassInfo classInfo) {
		this.classes.add(classInfo);
	}

	public void addRelationship(Relationship relationship) {
		this.relationships.add(relationship);
	}

	public ArrayList<ClassInfo> getClasses() {
		return classes;
	}

	public ArrayList<Relationship> getRelationships() {
		return relationships;
	}

	public ModelSource getModelSource() {
		return modelSource;
	}

	public ClassInfo findClassByName(String name) {
		for (ClassInfo classInfo : classes) {
			if (classInfo.getName().equals(name)) {
				return classInfo;
			}
		}
		return null;
	}

	public void addWarning(String message) {
		warnings.add(message);
	}

	public void removeWarningsForClass(String className) {
		String pattern = "Class '" + className + "' not found in source files. Added as dummy.";
		warnings.removeIf(w -> w.equals(pattern));
	}

	public List<String> getWarnings() {
		return warnings;
	}

	public void setRelationships(ArrayList<Relationship> relationships) {
		this.relationships = relationships;
	}

	public void setClasses(ArrayList<ClassInfo> classes) {
		this.classes = classes;
	}

}
