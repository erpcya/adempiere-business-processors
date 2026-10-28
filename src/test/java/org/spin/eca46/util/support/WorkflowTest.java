package org.spin.eca46.util.support;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.compiere.wf.MWorkflowProcessor;
import org.compiere.model.PO;
import org.compiere.model.POInfo;
import org.compiere.model.POInfoColumn;
import org.junit.jupiter.api.Test;

class WorkflowTest {

	@Test
	void newInstanceCreatesWorkflow() {
		assertNotNull(Workflow.newInstance());
	}

	@Test
	void withWorkflowProcessorAcceptsNullAndReturnsSameWorkflow() {
		Workflow workflow = Workflow.newInstance();

		assertSame(workflow, workflow.withWorkflowProcessor(null));
	}

	@Test
	void isEnabledReturnsWorkflowProcessorActiveState() throws ReflectiveOperationException {
		MWorkflowProcessor processor = activeWorkflowProcessor();
		Workflow workflow = Workflow.newInstance().withWorkflowProcessor(processor);

		assertTrue(workflow.isEnabled());
	}

	private static MWorkflowProcessor activeWorkflowProcessor() throws ReflectiveOperationException {
		MWorkflowProcessor processor = allocateWithoutConstructor(MWorkflowProcessor.class);
		POInfo info = allocateWithoutConstructor(POInfo.class);
		POInfoColumn activeColumn = allocateWithoutConstructor(POInfoColumn.class);
		activeColumn.ColumnName = "IsActive";
		setField(info, POInfo.class, "columns", new POInfoColumn[] { activeColumn });
		setField(processor, PO.class, "p_info", info);
		setField(processor, PO.class, "m_newValues", new Object[] { Boolean.TRUE });
		return processor;
	}

	private static void setField(Object target, Class<?> declaringType, String name, Object value)
			throws ReflectiveOperationException {
		Field field = declaringType.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static <T> T allocateWithoutConstructor(Class<T> type) throws ReflectiveOperationException {
		Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
		Field unsafeField = unsafeType.getDeclaredField("theUnsafe");
		unsafeField.setAccessible(true);
		Object unsafe = unsafeField.get(null);
		Method allocateInstance = unsafeType.getMethod("allocateInstance", Class.class);
		return type.cast(allocateInstance.invoke(unsafe, type));
	}
}
