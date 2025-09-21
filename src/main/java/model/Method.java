package model;

import java.util.ArrayList;

public class Method {
    private String name;
    private String returnType;
    private ArrayList<String> parameters;
    //private boolean isAbstract;
    private String visibility; // - + #
    private boolean isStatic;
    
    /*public Method(String name, String returnType, ArrayList<String> parameters, boolean isAbstract, String visibility) {
        this.name = name;
        this.returnType = returnType;
        this.parameters = parameters;
        this.isAbstract = isAbstract;
        this.visibility = visibility;
    }*/
    
    public Method(String name, String returnType, ArrayList<String> parameters, String visibility) {
        this.name = name;
        this.returnType = returnType;
        this.parameters = parameters;
        this.visibility = visibility;
        this. isStatic = false;
    
    /*public boolean isAbstract() {
        return isAbstract;*/
    }
    
    public Method(String name, String returnType, String visibility) {
        this.name = name;
        this.returnType = returnType;
        this.parameters = new ArrayList<String>();
        this.visibility = visibility;
        this.isStatic = false;
    }

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getReturnType() {
		return returnType;
	}

	public void setReturnType(String returnType) {
		this.returnType = returnType;
	}

	public ArrayList<String> getParameters() {
		return parameters;
	}
	
    public String getVisibility() {
        return visibility;
    }
    
    // Only the type is kept in the Intermediate Model
    public void addParameter(String parameter) {
    	parameters.add(parameter);
    }

	public boolean isStatic() {
		return isStatic;
	}

	public void setStatic(boolean isStatic) {
		this.isStatic = isStatic;
	}

}

