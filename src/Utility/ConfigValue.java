package Utility;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.prefs.Preferences;



public final class ConfigValue {

    private final Preferences m_prefNode;
    private final String m_defaultValue;
    
    

    public ConfigValue(Preferences prefNode,
                       String defaultValue,
                       Function<String, Boolean> valueManipulator) {
        
        m_prefNode = prefNode;
        m_prefNode.addPreferenceChangeListener(pl -> {
            
            // Update value as soon as the configuration changes or set it
            // back to default if there was a problem while doing so.
            if(!valueManipulator.apply(pl.getNewValue()))
                m_prefNode.put("value", defaultValue);
        });

        m_defaultValue = defaultValue;
    }
    
    
    
    public void addChangeListener(Consumer<String> listener) {

        m_prefNode.addPreferenceChangeListener(pl -> listener.accept(pl.getNewValue()));
    }

    public void setValue(String value) {

        m_prefNode.put("value", value);
    }

    public void initValue() {

        // This informs all listeners about the current value
        setValue(m_prefNode.get("value", m_defaultValue));
    }
}
