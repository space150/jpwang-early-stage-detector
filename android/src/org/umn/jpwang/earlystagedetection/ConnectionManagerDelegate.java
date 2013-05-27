package org.umn.jpwang.earlystagedetection;

public interface ConnectionManagerDelegate
{
    public void connected(boolean success);
    public void disconnected();
    public void message(String message);
}
