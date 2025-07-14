package model;

import java.util.ArrayList;

public class IntermediateModel {
	
	private ArrayList<ClassInfo> classes; // All parsed classes/interfaces/etc.
	private ArrayList<Relationship> relationships; // All relationships between the classes
	private ModelSource modelSource;

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
}
