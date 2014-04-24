package com.identityblitz.login.glue.play

import play.api.mvc.Codec
import play.api.http.{ContentTypes, ContentTypeOf, Writeable}
import com.identityblitz.json.JVal

object JUtil {

  implicit def writeableOf_JVal(implicit codec: Codec): Writeable[JVal] = {
    Writeable(jsval => codec.encode(jsval.toJson))
  }

  implicit def contentTypeOf_JVal(implicit codec: Codec): ContentTypeOf[JVal] = {
    ContentTypeOf[JVal](Some(ContentTypes.JSON))
  }

}
