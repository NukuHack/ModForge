package modforge.backend.model.attributes;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EnumAttribute extends BaseAttribute<Enum<?>> {
    @Getter
    private final Class<? extends Enum<?>> enumType;
    
    public EnumAttribute(String name, Enum<?> value) {
        super(name, value);
        this.enumType = value.getDeclaringClass();
    }
    
    @Override
    public EnumAttribute deepClone() {
        return this;
    }
    
    @Override
    public EnumAttribute deepClone(Enum<?> newValue) {
        return new EnumAttribute(name, newValue);
    }
}