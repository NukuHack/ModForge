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
	void classTest4() {
		ModItem item = Mockito.mock(ModItem.class, Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
		Mockito.doReturn(ModItem.BaseModItem.class).when(item).getClass();
		// this returns "com.nukuhack.modforge.backend.model.ModItem$MockitoMock$"+(7 random char)
		log.debug("class: {}", item.getClass());
	}
	
	@Test
	void classTest5() {
		ModItem item = Mockito.mock(ModItem.BaseModItem.class, Mockito.CALLS_REAL_METHODS);
		// this
		log.debug("class: {}", item.getClass());
	}
	@Test
	void classTest6() throws Exception {
		Class<? extends ModItem> dynamicType = new ByteBuddy()
			   .subclass(ModItem.BaseModItem.class)
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