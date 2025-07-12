package model;

public class Attribute {
    private String name;
    private String type; // int, String, etc
    private String visibility; // - + #
    
    public Attribute(String name, String type, String visibility) {
        this.name = name;
        this.type = type;
        this.visibility = visibility;
    }

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
	
    public String getVisibility() {
        return visibility;
    }
}

