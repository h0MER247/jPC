/*
 * Copyright (C) 2017 h0MER247
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package Main.Systems;

import Utility.ConfigValue;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.prefs.Preferences;



public final class ComponentConfig {
    
    public enum Type {
        
        ToggleGroup, ToggleValue, FileValue
    }
    private final Type m_type;
    private final String m_category;
    private final String m_label;
    private final ConfigValue m_value;
    private final List<String[]> m_options;
    private final boolean m_isResettingSystem;

    
    
    private ComponentConfig(Builder builder) {
        
        Preferences node = Preferences.userRoot()
                                      .node("jPC")
                                      .node(builder.m_system)
                                      .node(builder.m_category)
                                      .node(builder.m_label);
        
        m_type = builder.m_type;
        m_category = builder.m_category;
        m_label = builder.m_label;
        m_value = new ConfigValue(node, builder.m_defaultValue, builder.m_valueManipulator);
        m_options = builder.m_options;
        m_isResettingSystem = builder.m_isResettingSystem;
    }
    
    
    
    public void addChangeListener(Consumer<String> listener) {
        
        m_value.addChangeListener(listener);
    }
    
    public void setValue(String value) {

        m_value.setValue(value);
    }
    
    public Type getType() {
        
        return m_type;
    }
    
    public String getCategory() {

        return m_category;
    }

    public String getLabel() {

        return m_label;
    }
    
    public List<String[]> getOptions() {

        return m_options;
    }
    
    public void initValue() {
        
        m_value.initValue();
    }
    
    public boolean isResettingSystem() {
        
        return m_isResettingSystem;
    }
    
    
    
    public static final class Builder {
        
        private final String m_system;
        private final String m_category;
        private final Consumer<ComponentConfig> m_buildResultListener;
        
        private String m_label;
        private String m_defaultValue;
        private Type m_type;
        private boolean m_isResettingSystem;
        private Function<String, Boolean> m_valueManipulator;
        private final List<String[]> m_options;
        
        public Builder(String system,
                       String category,
                       Consumer<ComponentConfig> buildResultListener) {
            
            m_system = system;
            m_category = category;
            m_buildResultListener = buildResultListener;
            m_options = new ArrayList<>();
        }
        
        public Builder value(String label,
                             String defaultValue,
                             Type type,
                             Function<String, Boolean> valueManipulator) {
            
            m_type = type;
            m_label = label;
            m_defaultValue = defaultValue;
            m_valueManipulator = valueManipulator;
            return this;
        }
        
        public Builder option(String label, String value) {

            m_options.add(new String[] { label, value });
            return this;
        }

        public Builder isResettingSystem() {

            m_isResettingSystem = true;
            return this;
        }
        
        public void build() {
            
            if(m_type == Type.ToggleGroup && m_options.isEmpty())
                throw new IllegalArgumentException("A toggle group has to have at least one option");
            
            m_buildResultListener.accept(new ComponentConfig(this));
        }
    };
}
