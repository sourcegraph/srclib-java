package com.sourcegraph.javagraph;

class DepResolution {
    RawDependency Raw;
    ResolvedTarget Target;
    String Error;

    public DepResolution(RawDependency raw, ResolvedTarget target) {
        this.Raw = raw;
        this.Target = target;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DepResolution that = (DepResolution) o;

        if (Error != null ? !Error.equals(that.Error) : that.Error != null) return false;
        if (Raw != null ? !Raw.equals(that.Raw) : that.Raw != null) return false;
        if (Target != null ? !Target.equals(that.Target) : that.Target != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = Raw != null ? Raw.hashCode() : 0;
        result = 31 * result + (Target != null ? Target.hashCode() : 0);
        result = 31 * result + (Error != null ? this.Error.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DepResolution{" +
                "Target=" + Target +
                ", Error='" + Error + '\'' +
                '}';
    }
}
