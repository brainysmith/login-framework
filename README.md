LOGIN FRAMEWORK
===============

Requires
========
* Please download latest version of SBT.
* [sbt 0.13.1](https://scala-sbt.org)

Architecture
============
The login framework consists of 
* control flow.
* authentication methods.
* binding and attributes providers.

Control flow
------------
The control flow manages the authentication process and is starting point of the authentication process. It accepts 
the authentication request and drives it thorough the necessary authentication methods. At the end of the process 
it returns the authentication result to the point where the request was received from. 
The configuration of the control flow is placed in section *login-flow*. The framework has the default control flow 
*DefaultLoginFlow* used when a class of another control flow is not specified in the *login-flow* section, by key *class*.
Example of the default control flow's configuration:
```
login-framework {
    ...
    login-flow {
	    steps = {
	        "1" = "currentSession:sufficient"
	        "2" = pswd
	    }
    }
    ...
}
```.
In the section *steps* there are steps of the login flow. The each entry specifies the authentication method included in 
the login flow. The key is the number of the step the method is run on. The value has two elements. The first is method 
name and the second is method's flag. There are three possible values:
 * sufficient; 
 * required; 
 * optional.
If the method flag is not specified *required* used.






#Use
TODO

#Author
