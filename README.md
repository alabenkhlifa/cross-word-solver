# cross-word-solver
Cross word solver is a small library that help you search for words in an image

The image provided should be a valid matrix of letters with the same width and height

The first step is to build the jar with this command
```
gradlew buildFatJar
```

![N|Solid](https://github.com/alabenkhlifa/cross-word-solver/blob/master/howToBuildFatJar.png)

after that you should find your fat jar in the build/libs, copy it and paste it in a new directory

after that you should add a valid cross word image and the dictionary provided in the git repository (words_alpha.txt)

You're all set

To run the jar, write this command , and specify the search directions (should be one of these for the moment : RIGHT LEFT UP DOWN)

```
java -jar yourJarName.jar imagePath.png direction1 direction2 direction3 direction4
```

replace yourJarName.jar by the generated jar name

and imagePath.png by the name of your cross words image

and directions by search directions (RIGHT LEFT UP DOWN)

eg


```
java -jar crosswords.jar crossword.png UP DOWN RIGHT LEFT
```

This is the actual result

![N|Solid](https://github.com/alabenkhlifa/cross-word-solver/blob/master/actualResult.png)
