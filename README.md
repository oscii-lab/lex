# The Oscii Lexicon

A research project to learn, index, and display all lexical information.

## Building

* Clone `https://github.com/stanfordnlp/CoreNLP.git` and set `CORENLP_HOME` to
  the path to this repository directory on your local machine.
* Clone `https://github.com/stanfordnlp/phrasal.git` and set `PHRASAL_HOME` to
  the path to this repository directory on your local machine.
* Run `gradle test` to build and test `lex`.

## IDEA

To run `gradle` within Intellij IDEA, the cloned versions of `CoreNLP` and `phrasal` must
be in a sub-directory of `lex`, such as `deps`. 

## Getting started

The lexicon uses [PanLex](http://panlex.org/) data as a seed. To run the
system,
* [download](http://dev.panlex.org/db/) a `JSON`-formatted archive of PanLex data.
* To compile a lexicon as a `lex.json` file, execute `gradle run -Pargs="-p /path/to/panlex/directory -w /path/to/lex.json"`
* To serve translations using rabbitmq, execute `gradle run -Pargs="-r /path/to/lex.json -s"`

## API

The lexicon is currently served from: `http://104.197.10.176/translate/lexicon` 

The structure of a request object is defined in `org.oscii.api.Protocol.Request`.

Responses are served in three ways:
* Over rabbitmq on the `lexicon` queue in response to a JSON request
* Over http in response to a JSON request body
* Over http in response to request parameters defining the fields of a request

For example, the following request returns translations, definitions, and
extensions for the word "drive".

    http://104.197.10.176/translate/lexicon?query=drive&source=en&target=es&translate=true&extend=true&define=true

