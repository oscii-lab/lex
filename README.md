# The Oscii Lexicon

A research project to learn, index, and display all lexical information.

## Dependencies

You have to clone the following repositories as a pre-requisite:

```
 $ git clone git@github.com:stanfordnlp/phrasal.git
 $ git clone git@github.com:stanfordnlp/CoreNLP.git
 $ git clone git@github.com:oscii-lab/Word2VecJava.git
```

## Building

* Run `gradle test` to build and test `lex`.

## IDEA

* Run `gradle idea` to create project files.

## Getting started

The lexicon uses [PanLex](http://panlex.org/) data as a seed. To run the
system,
* [download](http://dev.panlex.org/db/) a `JSON`-formatted archive of PanLex data.
* To compile a lexicon as a `lex.json` file,  execute 
  `gradle :run -Pargs="-p /path/to/panlex/directory -w /path/to/lex.json"`
* To serve the lexicon and a corpus over `http`, execute 
  `gradle :run -Pargs="-r /path/to/lex.json -c /path/to/corpus/prefix -a"`

## API

The lexicon is currently served from: `http://104.197.10.176/translate/lexicon` 

The structure of a request object is defined in `org.oscii.api.Protocol.Request`.

Responses are served in two ways:
* Over http in response to a JSON request body
* Over http in response to request parameters defining the fields of a request

For example, the following request returns translations, definitions, and
extensions for the word "drive".

    http://104.197.10.176/translate/lexicon?query=drive&source=en&target=es&translate=true&extend=true&define=true
