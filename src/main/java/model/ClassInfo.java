package model;

import java.util.ArrayList;

public class ClassInfo {
	private String name;
	private ClassType classType; // CLASS, INTERFACE, ENUM
	private boolean isAbstract = false;
	private ArrayList<Attribute> attributes;
	private ArrayList<Method> methods;
	
	public ClassInfo(String name, ClassType classType) {
		this.name = name;
		this.classType = classType;
		this.attributes = new ArrayList<>();
		this.methods = new ArrayList<>();
	}
	
	public ClassInfo(String name, ClassType classType, boolean isAbstract) {
		this.name = name;
		this.classType = classType;
		this.isAbstract = isAbstract;
		this.attributes = new ArrayList<>();
		this.methods = new ArrayList<>();
	}
	
	// Add attribute (only allowed for classes)
	public void addAttribute(Attribute attribute){
		/*if(classType != ClassType.CLASS  || isAbstract) {
			throw new IllegalArgumentException("Only non-abstract classes have attributes.");
		}*/
		this.attributes.add(attribute);
	}
	
	// Add method (interfaces allow only abstract)
    public void addMethod(Method method) {
        /*if ((classType == ClassType.INTERFACE || isAbstract) && !method.isAbstract()) {
            throw new IllegalArgumentException("Interfaces and abstract classes can only have abstract methods.");
        }*/
        this.methods.add(method);
    }

    public String getName() {
        return name;
    }
    
    public void setAbstract(boolean isAbstract) {
        this.isAbstract = isAbstract;
    }
    
    public boolean isAbstract() {
        return isAbstract;
    }

    public ClassType getClassType() {
        return classType;
    }

    public ArrayList<Attribute> getAttributes() {
        return attributes;
    }

    public ArrayList<Method> getMethods() {
        return methods;
    }

}
