# ISSUES

### when I forgot to change the name property to word on org.mpierce.ktordemo.NewWordRequest

Symptom:
```shell
% curl -i -XPOST -d '{"word":"zymurgy"}' -H "Content-Type: application/json" http://localhost:9080/words  
HTTP/1.1 404 Not Found
Content-Length: 0
```
Log:
2023-11-15T21:33:28.830 [eventLoopGroupProxy-4-4] WARN  o.m.ktordemo.cli.RunServer$Companion - Unhandled exception
com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException: Instantiation of [simple type, class org.mpierce.ktordemo.NewWidgetRequest] 
value failed for JSON property name due to missing (therefore NULL) value for creator parameter name which is a non-nullable type

Problem:
Should be a 400 because it's a bad param, not 404