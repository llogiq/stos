# stos
An introspection- and code-generation-less Java-hosted Forth

This is basically a proof-of-concept of a 16-bit forth VM that is running within a Java program without ever using reflection
or byte code generation. There are a few hard coded (internal) words, and some words are built in forth itself.

To build, compile, run Internals.java and compile the resulting file.
(I really need to add an ant script :smile:)
