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

## Application Screenshots

The following screenshots illustrate the main workflow of PlantUMLCheck.

### 1. File Upload
<img width="1153" height="552" alt="PlantUMLCheck File Upload Screen" src="https://github.com/user-attachments/assets/b6bdbb83-1c67-413e-af38-baab69f9b17f" />

Users can upload Java source files and optional PlantUML diagrams. Code-only mode is also available for generating UML diagrams directly from Java source code.

### 2. Class Selection
<img width="1153" height="562" alt="PlantUMLCheck Class Selection Screen" src="https://github.com/user-attachments/assets/7c286c5e-f5ce-459d-8f27-5cdcf1251fbf" />

Users can select which classes to analyze and choose between three comparison modes: Strict, Relaxed, and Minimal.

### 3. Comparison Results

The application displays the results of the consistency check, including statistics and detected differences.

**Successful Comparison**

<img width="1115" height="417" alt="PlantUMLCheck Successful Comparison" src="https://github.com/user-attachments/assets/4cc3d061-1da1-4d82-bb28-d16fc9d0cb81" />

**Failed Comparison**

<img width="1115" height="541" alt="PlantUMLCheck Detected Inconsistencies" src="https://github.com/user-attachments/assets/c09dbe47-db92-41ac-bec0-d90da3a34cd5" />

Detected inconsistencies are listed with their severity levels. Users can export a report or generate a corrected UML diagram.

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
