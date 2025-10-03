# PlantUMLCheck

PlantUMLCheck is a tool developed as part of a diploma thesis at the University of Ioannina.  
It provides automated consistency checking between UML class diagrams (in PlantUML format) and Java source code.

## Overview
The motivation for this project is the common problem of divergence between UML documentation and actual source code during software evolution. PlantUMLCheck addresses this issue by:

- Parsing both Java source code and PlantUML diagrams into a unified intermediate model.
- Comparing the two models using predefined consistency rules.
- Reporting discrepancies with clear categorization (errors, warnings, suggestions, information).
- Offering the option to generate a corrected UML diagram that matches the code.

The tool supports different levels of strictness in checking, allowing users to adapt the analysis to their needs.

## Features
- Java and PlantUML parsers implemented with regular expressions.
- Intermediate model for uniform representation of classes, attributes, methods, and relationships.
- Configurable consistency checking (Strict, Relaxed, Minimal).
- Report generation in text format with categorized findings.
- Automatic UML diagram generation from code or corrected model.
- Web-based interface built with Spring Boot.

## Project Structure
- `model/`: Core intermediate model classes.
- `parser.code/`: Java source code parser.
- `parser.uml/`: PlantUML parser.
- `comparison/`: Consistency checking engine.
- `generator/`: UML diagram generator.
- `controller/` and `service/`: Spring Boot backend and services.

## Technology Stack
- Java 17  
- Spring Boot  
- Maven  
- JUnit 5  

## Installation
Clone the repository and build with Maven:

```bash
git clone https://github.com/elenigki/PlantUMLCheck.git
cd PlantUMLCheck
mvn clean install
```

## Usage
Run the application:

```bash
mvn spring-boot:run
```

The application starts at `http://localhost:8080`.

Through the web interface you can:
1. Upload Java files and optionally UML diagrams.
2. Select classes/packages and checking mode.
3. View results and export reports or corrected diagrams.

## Testing
The project includes extensive unit tests covering parsing, model comparison, and UML generation.  
The tests are written with JUnit 5.

## Thesis
This project was developed as part of the diploma thesis:  
**"Automated Consistency Checking of PlantUML Diagrams Against Java Source Code"**  
University of Ioannina, Department of Computer Science & Engineering, 2025.
