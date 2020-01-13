package gov.nasa.jpl.mbee.mdk.test.framework;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.runtime.ApplicationExitedException;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

/**
 * Created by igomes on 10/21/16.
 */

public class ApplicationStartClassRunner extends BlockJUnit4ClassRunner {
    public ApplicationStartClassRunner(Class<?> clazz) throws InitializationError {
        super(clazz);

        try {
            Application.getInstance().start(true, true, false, new String[]{"TESTER"}, null);
        } catch (ApplicationExitedException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}