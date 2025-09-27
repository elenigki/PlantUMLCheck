package model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ClassInfo {
	private String name;
	private ClassType classType; // CLASS, INTERFACE, ENUM
	private boolean isAbstract = false;
	private ArrayList<Attribute> attributes;
	private ArrayList<Method> methods;
	private ClassDeclaration declaration;
	private ArrayList<String> enumConstants;
	
	public ClassInfo(String name, ClassType classType, ClassDeclaration declaration) {
		this.name = name;
		this.classType = classType;
		this.attributes = new ArrayList<>();
		this.methods = new ArrayList<>();
		this.declaration = declaration;
		this.enumConstants = new ArrayList<>();
	}
	
	public ClassInfo(String name, ClassType classType, boolean isAbstract, ClassDeclaration declaration) {
		this.name = name;
		this.classType = classType;
		this.isAbstract = isAbstract;
		this.attributes = new ArrayList<>();
		this.methods = new ArrayList<>();
		this.declaration = declaration;
		this.enumConstants = new ArrayList<>();
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
    
    public void setClassType(ClassType classType) {
        this.classType = classType;
    }

    public ArrayList<Attribute> getAttributes() {
        return attributes;
    }

    public ArrayList<Method> getMethods() {
        return methods;
    }
    
    // store extra data related to this class
    private Map<String, Object> metadata = new HashMap<>();

    // set arbitrary metadata by key
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    // get stored metadata by key
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

	public ClassDeclaration getDeclaration() {
		return declaration;
	}

	public void setDeclaration(ClassDeclaration declaration) {
		this.declaration = declaration;
	}

	public ArrayList<String> getEnumConstants() {
		return enumConstants;
	}
	
	public void addEnumConstants(String constant) {
		enumConstants.add(constant);
	}


}
