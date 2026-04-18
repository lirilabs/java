#!/bin/bash

echo "Compiling..."

javac --add-modules jdk.httpserver Main.java

echo "Running..."

java --add-modules jdk.httpserver Main
