package com.doceleguas.pos.webservices;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.mobile.core.master.MasterDataProcessHQLQuery.MasterDataModel;

public abstract class Model {
  public abstract JSONArray exec(JSONObject jsonsent) throws JSONException;

  public abstract String getName();

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ ElementType.TYPE, ElementType.FIELD })
  public @interface ModelAnnotation {
    String value();

    /**
     * Supports instantiation of the {@link MasterDataModel} qualifier. It can be used to select a
     * master data model (MasterDataProcessHQLQuery instance) in particular.
     */
    @SuppressWarnings("all")
    public static final class Literal extends AnnotationLiteral<ModelAnnotation>
        implements ModelAnnotation {
      private static final long serialVersionUID = 1L;

      private final String value;

      public Literal(String value) {
        this.value = value;
      }

      @Override
      public String value() {
        return value;
      }
    }
  }
}
