Project documentation

# IDE

## How to properly format Java code

The file  `./ide/code-formatter-settings.xml` can be used in Eclipse or VSCode. It is based on the default Eclipse style but add some changes to properly format fluent API calls.

On VSCode use the following setting:

``` 
"java.format.settings.url": "./ide/code-formatter-settings.xml",
```

On Eclipse go in Preference->Java->Code Style->Formatter->import

## Setup Lombok support

This project uses the [Project Lombok](https://projectlombok.org/) framework on top of SpringBoot. If you don't configure properly your IDE you will have tons of "not defined" errors.

- As far as I know there is no support for VSCode
- There is a plugin for Eclipse

According to [this post](https://stackoverflow.com/a/46034044), you have to do this for Eclipse:


- Exit Eclipse(if it is open) and download the jar from [https://projectlombok.org/download](https://projectlombok.org/download)
- Execute command: `java -jar lombok.jar`
- This command will open window as shown [here](https://projectlombok.org/setup/eclipse), install and quit the installer.
- Add jar to build path/add it to pom.xml.
- Restart eclipse.
- Go to Eclipse --> About Eclipse --> check 'Lombok v1.16.18 "Dancing Elephant" is installed. https://projectlombok.org/'
- To enable Lombok for the project: **Enable annotation processing** in the project settings (Java compiler->Annotation processing). 

## Understand what Lombok does to your code

Lombok use annotations to inject getters and setters so you don't have to write them. It means that it is acting as a preprocessor and generate an alternate .java/class file that will be debugged and shipped. 

The drawback: 
- You are writing code that cannot be understood by any IDE without a plugin.
- Without a Lombok plugin, your IDE will complain that you didn't wrote the getters and setters.

# Environment variables and Debug

This project use Springboot annotation `@Value` which can read values from environments variables. This is especially useful in a Docker container.
In order to be able to debug under Eclipse you will have to set some environment variables:

- MAPPINGS: mappings location
- SPRING_CLOUD_QUARTZ_ENABLED: false
- SPRING_CLOUD_VAULT_ENABLED: false
- SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/etl

then run a `maven spring-boot:run` in Debug.

Note: of course you need to run a Postgresql database somewhere


 


