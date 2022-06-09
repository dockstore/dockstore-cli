task hello {
  command {
    echo 'Hello!'
  }
  output {
    File response = stdout()
  }
}

workflow test {
  call hello
}
