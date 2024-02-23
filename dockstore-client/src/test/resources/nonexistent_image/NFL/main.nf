#!/usr/bin/env nextflow
params.str = 'Hello'

process sayHello {
  input:
  val x from params.str
  script:
  """
  echo '$x world!'
  """
}

workflow {
    sayHello
}