package parser.code;
import model.ClassType;
import java.io.File;

public class ScannedJavaInfo {
	private String fullyQualifiedName;
	private String className;
	private String packageName;
	private ClassType classType;
	private File sourceFile;
	
    public ScannedJavaInfo(String fullyQualifiedName, String className, String packageName,
            ClassType classType, File sourceFile) {
    	this.fullyQualifiedName = fullyQualifiedName;
    	this.className = className;
    	this.packageName = packageName;
    	this.classType = classType;
    	this.sourceFile = sourceFile;
    }

    public String getFullyQualifiedName() {
        return fullyQualifiedName;
    }

    public String getClassName() {
        return className;
    }

    public String getPackageName() {
        return packageName;
    }

    public ClassType getClassType() {
        return classType;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    @Override
    public String toString() {
        return classType + " " + fullyQualifiedName;
    }
}
