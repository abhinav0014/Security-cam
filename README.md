# Android Development Container

This project sets up a development container for Android development using Java and Kotlin. It provides a consistent environment for building and testing Android applications.

## Project Structure

```
android-devcontainer
├── .devcontainer
│   ├── devcontainer.json
│   └── Dockerfile
├── app
│   ├── src
│   │   ├── main
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java
│   │   │   │   └── com
│   │   │   │       └── example
│   │   │   │           └── myapp
│   │   │   │               ├── MainActivity.kt
│   │   │   │               └── ExampleJavaClass.java
│   │   │   └── res
│   │   │       ├── layout
│   │   │       │   └── activity_main.xml
│   │   │       └── values
│   │   │           └── strings.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradle
│   └── wrapper
│       └── gradle-wrapper.properties
├── gradlew
├── gradlew.bat
├── .gitignore
└── README.md
```

## Getting Started

1. **Clone the Repository**
   ```bash
   git clone <repository-url>
   cd android-devcontainer
   ```

2. **Open in Development Container**
   Use your IDE to open the project in the development container. This will set up the environment automatically.

3. **Build the Project**
   Run the following command to build the project:
   ```bash
   ./gradlew build
   ```

4. **Run the Application**
   You can run the application on an emulator or a connected device using:
   ```bash
   ./gradlew installDebug
   ```

## Dependencies

This project uses Gradle for build automation. Ensure you have the necessary Android SDK components installed in the development container.

## Contributing

Feel free to submit issues or pull requests for improvements or bug fixes.

## License

This project is licensed under the MIT License. See the LICENSE file for details.