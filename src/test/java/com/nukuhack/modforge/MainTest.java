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
	void classTest1() {
		final ModItem item = Mockito.spy(new ModItem.EmptyImpl());
		Mockito.when(item.getClass()).thenReturn((Class) ModItem.BaseModItem.class);
		// this throws "org.mockito.exceptions.misusing.MissingMethodInvocationException"
		log.debug("class: {}", item.getClass());
	}
	
	@Test
	void classTest2() {
		final ModItem item = Mockito.spy(new ModItem.EmptyImpl());
		Mockito.doReturn(ModItem.BaseModItem.class).when(item).getClass();
		// this return the "ModItem$EmptyImpl"
		log.debug("class: {}", item.getClass());
	}
	
	@Test
	void classTest3() {
		ModItem item = Mockito.mock(ModItem.EmptyImpl.class, Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
		Mockito.doReturn(ModItem.BaseModItem.class).when(item).getClass();
		// same as test 1
		log.debug("class: {}", item.getClass());
	}
	
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