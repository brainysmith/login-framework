package com.identityblitz.lang.java;

import scala.Predef;
import scala.Tuple2;
import scala.collection.convert.WrapAsJava$;
import scala.collection.convert.WrapAsScala$;
import scala.collection.immutable.Map;

/**
  */
public class ToScalaConverter {

    public static <A, B> Map<A, B> toScalaImmMap(java.util.Map<A, B> m) {
        return WrapAsScala$.MODULE$.mapAsScalaMap(m).toMap(
                Predef.<Tuple2<A, B>>conforms()
        );
    }
}
