#!/usr/bin/env nextflow
nextflow.enable.dsl=2
process sayHello {
  input:
    val x
  output:
    stdout
  script:
    """
    echo '$x world!'
    """
}

workflow {
  Channel.of('results') | sayHello | view
}