package model;

import comparison.CheckMode;

public class Relationship {
	private ClassInfo sourceClass;
	private ClassInfo targetClass;
	private RelationshipType type;
	private CheckMode strictnessLevel = CheckMode.STRICT; // default STRICT

	public Relationship(ClassInfo sourceClass, ClassInfo targetClass, RelationshipType type) {
		this.sourceClass = sourceClass;
		this.targetClass = targetClass;
		this.type = type;
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

	public CheckMode getStrictnessLevel() {
		return strictnessLevel;
	}

	public void setStrictnessLevel(CheckMode level) {
		this.strictnessLevel = level;
	}

}
