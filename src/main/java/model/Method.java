package model;

import java.util.ArrayList;

public class Method {
    private String name;
    private String returnType;
    private ArrayList<String> parameters;
    //private boolean isAbstract;
    private String visibility; // - + #
    
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
    
    /*public boolean isAbstract() {
        return isAbstract;*/
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

	public void setParameters(ArrayList<String> parameters) {
		this.parameters = parameters;
	}
	
    public String getVisibility() {
        return visibility;
    }

}

