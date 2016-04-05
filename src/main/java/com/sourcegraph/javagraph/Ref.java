package com.sourcegraph.javagraph;

import com.google.gson.*;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Type;

/**
 * Reference to a definition object
 */
public class Ref {

    /**
     * Definition key
     */
    DefKey defKey;

    /**
     * Definition's repository
     */
    String defRepo;

    /**
     * Definitions's unit type
     */
    String defUnitType;

    /**
     * Defitinion's unit name
     */
    String defUnit;

    /**
     * File that holds a reference
     */
    String file;

    /**
     * Start offset in file
     */
    int start;

    /**
     * End offset in file
     */
    int end;

    /**
     * If reference is also a definition
     */
    boolean def;

    String unit;

    /**
     * Ref cannot exist without a unit
     * @param unit source unit
     */
    public Ref(String unit) {
        this.unit = unit;
    }

    public void setDefTarget(ResolvedTarget target) {
        defRepo = target.ToRepoCloneURL;
        defUnitType = target.ToUnitType;
        defUnit = target.ToUnit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Ref ref = (Ref) o;

        if (def != ref.def) return false;
        if (end != ref.end) return false;
        if (start != ref.start) return false;
        if (defKey != null ? !defKey.equals(ref.defKey) : ref.defKey != null) return false;
        if (defRepo != null ? !defRepo.equals(ref.defRepo) : ref.defRepo != null) return false;
        if (defUnit != null ? !defUnit.equals(ref.defUnit) : ref.defUnit != null) return false;
        if (defUnitType != null ? !defUnitType.equals(ref.defUnitType) : ref.defUnitType != null) return false;
        if (file != null ? !file.equals(ref.file) : ref.file != null) return false;
        if (!unit.equals(ref.unit)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = defKey != null ? defKey.hashCode() : 0;
        result = 31 * result + (defRepo != null ? defRepo.hashCode() : 0);
        result = 31 * result + (defUnitType != null ? defUnitType.hashCode() : 0);
        result = 31 * result + (defUnit != null ? defUnit.hashCode() : 0);
        result = 31 * result + (file != null ? file.hashCode() : 0);
        result = 31 * result + start;
        result = 31 * result + end;
        result = 31 * result + (def ? 1 : 0);
        result = 31 * result + unit.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Ref{" + defKey +" @" + file + ":" + start + "-" + end + (def ? " DEF" : StringUtils.EMPTY) + "}";
    }

    /**
     * JSON serialization rules for reference objects
     */
    static class JSONSerializer implements JsonSerializer<Ref> {

        @Override
        public JsonElement serialize(Ref ref, Type arg1, JsonSerializationContext arg2) {
            JsonObject object = new JsonObject();

            if (ref.defKey.getOrigin() != null) {
                // Add for easier debugging.
                object.addProperty("_JavaOrigin", ref.defKey.getOrigin().toString());
            }

            if (ref.defRepo != null) object.add("DefRepo", new JsonPrimitive(ref.defRepo));
            if (ref.defUnitType != null) object.add("DefUnitType", new JsonPrimitive(ref.defUnitType));
            if (ref.defUnit != null) object.add("DefUnit", new JsonPrimitive(ref.defUnit));
            object.add("DefPath", new JsonPrimitive(ref.defKey.formatPath()));

            object.add("File", new JsonPrimitive(PathUtil.relativizeCwd(ref.file)));
            object.add("Start", new JsonPrimitive(ref.start));
            object.add("End", new JsonPrimitive(ref.end));
            object.add("Def", new JsonPrimitive(ref.def));
            object.add("Unit", new JsonPrimitive(ref.unit));

            return object;
        }

    }
}
