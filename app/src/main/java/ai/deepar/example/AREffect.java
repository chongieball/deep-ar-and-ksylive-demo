package ai.deepar.example;

public class AREffect {

    public static final String EffectTypeMask = "mask";
    public static final String EffectTypeAction = "action";
    public static final String EffectTypeFilter = "filter";


    private String effectName;
    private String effectType;

    public AREffect(String effectName, String effectType) {
        this.effectName = effectName;
        this.effectType = effectType;
    }


    public String getEffectName() {
        return effectName;
    }

    public void setEffectName(String effectName) {
        this.effectName = effectName;
    }

    public String getEffectType() {
        return effectType;
    }

    public String getPath() {
        if (effectName.equals("none")) {
            return null;
        }
        return "file:///android_asset/" + effectName;
    }
}
