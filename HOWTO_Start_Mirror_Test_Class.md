There is a [directory mirror example](https://github.com/cryptomator/dokany-nio-adapter/blob/develop/src/test/java/org/cryptomator/frontend/dokany/ReadWriteMirrorTest.java), which you can start and then try to push it to its limit.

To run it properly, you need to fullfill some prerequisites:
* the build tool [maven](https://maven.apache.org/) in your path
* at least Java 9 installed. 
* in the mirror example file, the variable `path` must point to an existing directory on your system (note that for each `\` in your path, you need two slashes)
* in the mirror example file, the variable `mountPoint` must point to a free volume letter
* in the file `src/java/org/cryptomator/frontend/dokany/MountFactory`uncomment the mount option `MountOption.STD_ERR_OUTPUT` by removing the two leading slashes
* (optional) set `THREAD_COUNT` to 1

If this is the case, execute the following steps:
1. get the dokany-nio-adapter repository (either clone it with git or just download the zip-file and extract it)
1. open powershell and change the current path to the location of the repository
1. execute the following command: `mvn test`
1. if it succeeded, replace in the following command every occurence of [YOUR_USERNAME] with your actual windows username and execute it: 
`java -cp ".\target\test-classes;.\target\classes;C:\Users\[YOUR_USERNAME]\.m2\repository\com\google\guava\guava\27.0-jre\guava-27.0-jre.jar;C:\Users\[YOUR_USERNAME]\.m2\repository\net\java\dev\jna\jna\5.1.0\jna-5.1.0.jar;C:\Users\[YOUR_USERNAME]\.m2\repository\net\java\dev\jna\jna-platform\5.1.0\jna-platform-5.1.0.jar;C:\Users\[YOUR_USERNAME]\.m2\repository\commons-io\commons-io\2.6\commons-io-2.6.jar;C:\Users\[YOUR_USERNAME]\.m2\repository\org\slf4j\slf4j-api\1.7.25\slf4j-api-1.7.25.jar;C:\Users\[YOUR_USERNAME]\.m2\repository\org\slf4j\slf4j-simple\1.7.25\slf4j-simple-1.7.25.jar" org.cryptomator.frontend.dokany.ReadWriteMirrorTest > dokanyJava.log 2>&1`

Now the content of the directory should be visible under the volume letter of your choice in the windows explorer.
To stop it, change to the console and enter anything and hit enter. After it unmounted successfully you can exit it with `CMD`+`c`.

The log file is named `dokanyJava.log`will be placed in the repository directory.
