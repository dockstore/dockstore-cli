version 1.0
# This workflow test was taken directly from here https://github.com/openwdl/learn-wdl/tree/master/1_script_examples/1_hello_worlds/7_subworkflow

import "sub-workflow-task.wdl" as TestTask

workflow HelloWorld {
    input {
        String name
    }
    call TestTask.WriteGreeting {
        input:
            name = name
    }
}

