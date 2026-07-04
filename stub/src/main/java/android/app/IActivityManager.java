package android.app;

import android.content.ComponentName;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface IActivityManager extends IInterface {

    void startDelegateShellPermissionIdentity(int uid, String[] permissions) throws RemoteException;

    void stopDelegateShellPermissionIdentity() throws RemoteException;

    void stopDelegateShellPermissionIdentity(int uid) throws RemoteException;

    boolean startInstrumentation(ComponentName className, String profileFile,
                                  int flags, Bundle arguments, IInstrumentationWatcher watcher,
                                  IUiAutomationConnection connection, int userId,
                                  String abiOverride) throws RemoteException;

    abstract class Stub extends Binder implements IActivityManager {
        public static IActivityManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}
