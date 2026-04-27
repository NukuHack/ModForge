package com.nukuhack.modforge;

import com.nukuhack.modforge.backend.model.ModItem;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.StubMethod;
import net.bytebuddy.matcher.ElementMatchers;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@Slf4j
class MainTest {
	@Test
	void classTest() throws Exception {
		Class<? extends ModItem> dynamicType = new ByteBuddy()
			   .subclass(ModItem.class)
			   .method(ElementMatchers.any())
			   .intercept(StubMethod.INSTANCE)  // Returns null, 0, false, etc. based on return type
			   .make()
			   .load(getClass().getClassLoader())
			   .getLoaded();
		
		ModItem item = dynamicType.getDeclaredConstructor().newInstance();
		log.debug("class: {}", item.getClass());
		log.debug("langAttributes: {}", item.getLangAttributes()); // Returns null
	}
}