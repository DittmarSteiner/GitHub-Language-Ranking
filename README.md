# GitHub Language Ranking

This is a small project for a medium sophisticated exercise.  
The story is to analyze the data of one hour of a day on GitHub and report an evaluation.

The motivation here is to try and sharpen your Java 8 programming skills—in contrast to just googling some ready-to-use libraries. Everything can be achieved by the Java 8 Standard Library in an elegant, modern and performant way.

The benefit will appear when you present your approach to your colleagues.  
_Important: you do not even have to finish the whole project—we are all happy to discuss with you **one task after the other!**_

**Spoiler Alert:** if you really want to cheat on yourself, try the branch `dittmar/develop` :-(

## What we will learn
Java, Analysis, Modelling, I/O, Unit Testing, Java 8

## The tasks of your program

0. Retrieve the data from [http://data.githubarchive.org/2016-03-14-15.json.gz](http://data.githubarchive.org/2016-03-14-15.json.gz)
0. Analyze the data which is *“count the activities for each programming language”*
0. Order the result by the rank of the programming languages
0. Export the result as CSV, like *(the example is not real data!)*

| RANK | LANGUAGE              | ACTIVITIES |
| ---: | :-------------------- | ---------: |
| 1    | JavaScript            | 767        |
| 2    | Java                  | 547        |
| 3    | Python                | 515        |
| …    | …                     | …          |
| 78   | Apex                  | 1          |
| 79   | Prolog                | 1          |

Optionally you can also introduce a second order by language name.

As a highlight you may add another column “PROPORTION” with the two-digit percentage per language, like

| RANK | LANGUAGE              | ACTIVITIES | PROPORTION |
| ---: | :-------------------- | ---------: | ---------: |
| 1    | JavaScript            | 767        | 23.45 %    |
| …    | …                     | …          | …          |

#### Hint
*There might be different results but only one is correct ;-)*

## About Solutions
For the download you may use a library of your choice or just a plain [HttpURLConnection](https://docs.oracle.com/javase/8/docs/api/java/net/HttpURLConnection.html), if you like.  
But all other tasks fit perfectly in the Java 8 environment without any external libraries.

## Git
Branch early: create your own branch like  
`$ git checkout -b <your-name>/develop`  
Please, **do not push*** your solution—only to a private repository. Everyone should start plain from the beginning without any suggestion or template.

Now… **happy coding!**

