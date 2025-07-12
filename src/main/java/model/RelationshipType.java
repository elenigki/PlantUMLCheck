package model;

public enum RelationshipType {
    GENERALIZATION,   // class A extends B
    REALIZATION,      // class A implements I
    ASSOCIATION,      // attribute of type B
    AGGREGATION,      // attribute passed from outside
    COMPOSITION,      // attribute constructed inside
    DEPENDENCY        // method param/return/local usage
}
