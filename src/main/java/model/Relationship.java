package model;

public class Relationship {
    private ClassInfo sourceClass;
    private ClassInfo targetClass;
    private RelationshipType type;
    private StrictnessLevel strictnessLevel = StrictnessLevel.STRICT; // default STRICT
    //private String details; // multiplicity
    
    public Relationship(ClassInfo sourceClass, ClassInfo targetClass, RelationshipType type) {
        this.sourceClass = sourceClass;
        this.targetClass = targetClass;
        this.type = type;
        //this.details = details;
    }

	public ClassInfo getSourceClass() {
		return sourceClass;
	}

	public void setSourceClass(ClassInfo sourceClass) {
		this.sourceClass = sourceClass;
	}

	public ClassInfo getTargetClass() {
		return targetClass;
	}

	public void setTargetClass(ClassInfo targetClass) {
		this.targetClass = targetClass;
	}

	public RelationshipType getType() {
		return type;
	}

	public void setType(RelationshipType type) {
		this.type = type;
	}
	
    public StrictnessLevel getStrictnessLevel() {
        return strictnessLevel;
    }

    public void setStrictnessLevel(StrictnessLevel level) {
        this.strictnessLevel = level;
    }

	/*public String getDetails() {
		return details;
	}

	public void setDetails(String details) {
		this.details = details;
	}*/
    
    /*
    public static Relationship createRelationship(ClassInfo source, ClassInfo target, RelationshipType type, String details) {
        // Validation logic for relationships
        if (type == RelationshipType.GENERALIZATION) {
            if (source.getClassType() == ClassType.CLASS && target.getClassType() == ClassType.INTERFACE) {
                throw new IllegalArgumentException("A class cannot generalize (inherit) an interface.");
            }
            if (source.getClassType() == ClassType.INTERFACE && target.getClassType() != ClassType.INTERFACE) {
                throw new IllegalArgumentException("An interface can only generalize another interface.");
            }
        }
        if (type == RelationshipType.REALIZATION) {
            if (source.getClassType() != ClassType.CLASS || target.getClassType() != ClassType.INTERFACE) {
                throw new IllegalArgumentException("REALIZATION is only valid when a class implements an interface.");
            }
        }
        return new Relationship(source, target, type, details);
    }*/
    
    
}

