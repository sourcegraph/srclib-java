package com.sourcegraph.javagraph;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.List;

public class Def {
    DefKey defKey;
    String kind;
    String name;

    String file;
    int identStart;
    int identEnd;
    int defStart;
    int defEnd;

    List<String> modifiers;

    String pkg;

    String doc;

    String typeExpr;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Def def = (Def) o;

        if (defEnd != def.defEnd) return false;
        if (defStart != def.defStart) return false;
        if (identEnd != def.identEnd) return false;
        if (identStart != def.identStart) return false;
        if (defKey != null ? !defKey.equals(def.defKey) : def.defKey != null) return false;
        if (doc != null ? !doc.equals(def.doc) : def.doc != null) return false;
        if (file != null ? !file.equals(def.file) : def.file != null) return false;
        if (kind != null ? !kind.equals(def.kind) : def.kind != null) return false;
        if (modifiers != null ? !modifiers.equals(def.modifiers) : def.modifiers != null) return false;
        if (name != null ? !name.equals(def.name) : def.name != null) return false;
        if (pkg != null ? !pkg.equals(def.pkg) : def.pkg != null) return false;
        if (typeExpr != null ? !typeExpr.equals(def.typeExpr) : def.typeExpr != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = defKey != null ? defKey.hashCode() : 0;
        result = 31 * result + (kind != null ? kind.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (file != null ? file.hashCode() : 0);
        result = 31 * result + identStart;
        result = 31 * result + identEnd;
        result = 31 * result + defStart;
        result = 31 * result + defEnd;
        result = 31 * result + (modifiers != null ? modifiers.hashCode() : 0);
        result = 31 * result + (pkg != null ? pkg.hashCode() : 0);
        result = 31 * result + (doc != null ? doc.hashCode() : 0);
        result = 31 * result + (typeExpr != null ? typeExpr.hashCode() : 0);
        return result;
    }

    static class JSONSerializer implements JsonSerializer<Def> {
        @Override
        public JsonElement serialize(Def sym, Type arg1, JsonSerializationContext arg2) {
            JsonObject object = new JsonObject();

            if (sym.file != null)
                object.add("File", new JsonPrimitive(sym.file));

            object.add("Name", new JsonPrimitive(sym.name));

            object.add("DefStart", new JsonPrimitive(sym.defStart));
            object.add("DefEnd", new JsonPrimitive(sym.defEnd));

            boolean exported;
            if (sym.modifiers != null) {
                exported = sym.modifiers.contains("public");
                object.add("Exported", new JsonPrimitive(exported));
            } else {
                exported = false;
                object.add("Exported", new JsonPrimitive(false));
            }

            object.add("Local", new JsonPrimitive(!exported && !(sym.kind.equals("PACKAGE") || sym.kind.equals("ENUM") || sym.kind.equals("CLASS") || sym.kind.equals("ANNOTATION_TYPE") || sym.kind.equals("INTERFACE") || sym.kind.equals("ENUM_CONSTANT") || sym.kind.equals("FIELD") || sym.kind.equals("METHOD") || sym.kind.equals("CONSTRUCTOR"))));

            switch (sym.kind) {
                case "ENUM":
                case "CLASS":
                case "INTERFACE":
                case "ANNOTATION_TYPE":
                    object.add("Kind", new JsonPrimitive("type"));
                    break;
                case "METHOD":
                case "CONSTRUCTOR":
                    object.add("Kind", new JsonPrimitive("func"));
                    break;
                case "PACKAGE":
                    object.add("Kind", new JsonPrimitive("package"));
                    break;
                default:
                    object.add("Kind", new JsonPrimitive("var"));
                    break;
            }

            object.add("Path", new JsonPrimitive(sym.defKey.formatPath()));
            object.add("TreePath", new JsonPrimitive(sym.defKey.formatTreePath()));

            // Populate extra data field
            JsonObject data = new JsonObject();
            data.addProperty("JavaKind", sym.kind);
            data.addProperty("TypeExpression", sym.typeExpr);
            data.addProperty("Package", sym.pkg);

            if (sym.modifiers != null) {
                JsonArray modifiers = new JsonArray();
                for (String modifier : sym.modifiers) modifiers.add(new JsonPrimitive(modifier));
                data.add("Modifiers", modifiers);
            }

            object.add("Data", data);

            return object;
        }

    }
}
