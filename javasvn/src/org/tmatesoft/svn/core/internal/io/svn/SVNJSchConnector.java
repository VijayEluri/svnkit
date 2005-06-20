package org.tmatesoft.svn.core.internal.io.svn;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.io.ISVNCredentials;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNAuthenticationException;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.SVNUtil;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * @author Marc Strapetz
 * @author alex
 */
public class SVNJSchConnector implements ISVNConnector {

    private static final String CHANNEL_TYPE = "exec";
	private static final String SVNSERVE_COMMAND = "svnserve --tunnel";
	
	private ChannelExec myChannel;
    private InputStream myInputStream;
    private OutputStream myOutputStream;

    public void open(SVNRepositoryImpl repository) throws SVNException {
        ISVNCredentialsProvider provider = repository.getCredentialsProvider();
        if (provider == null) {
            throw new SVNException("Credentials provider is required for SSH connection");
        }
        provider.reset();

        // 1. fetch credentials for default user?
        String realm = repository.getLocation().toCanonicalForm();
        String userName = System.getProperty("user.name");
        ISVNCredentials credentials = SVNUtil.nextCredentials(provider, repository.getLocation(), realm);
        if (credentials != null) {
            userName = credentials.getName();
            if (userName != null) {
                realm = "svn+ssh://" + userName + "@" + realm.substring("svn+ssh://".length());
            }
        }
        SVNAuthenticationException lastException = null;
        Session session = null;
        
        while (credentials != null) {
            try {
            	session = SVNJSchSession.getSession(repository.getLocation(), credentials);
            	if (session != null && !session.isConnected()) {
            		session = null;
            		continue;
            	}
                provider.accepted(credentials);
                lastException = null;
                break;
            } catch (SVNAuthenticationException e) {
                provider.notAccepted(credentials, e.getMessage());
                if (session != null && session.isConnected()) {
            		DebugLog.log("DISCONNECTING: " + session);
            		session.disconnect();
            		session = null;
            	}
                lastException = e;
                credentials = SVNUtil.nextCredentials(provider, repository.getLocation(), realm);
            }
        }
        if (lastException != null || session == null) {
            if (lastException != null) {
                throw lastException;
            }
            throw new SVNAuthenticationException("Can't establish SSH connection without credentials");
        }
        repository.setCredentials(credentials);
        try {
            int retry = 1;
            while(true) {
	            myChannel = (ChannelExec) session.openChannel(CHANNEL_TYPE);
	            String command = SVNSERVE_COMMAND;
	            myChannel.setCommand(command);
	            
	            myOutputStream = myChannel.getOutputStream();
	            myInputStream = myChannel.getInputStream();
	
	            DebugLog.log("JSCH command: " + command);
            	try {
		            myChannel.connect();
            	} catch (JSchException e) {
            		retry--;
            		if (retry < 0) {
            			throw new SVNException(e);
            		}
            		if (session.isConnected()) {
            			session.disconnect();
            		}
            		continue;
            	}
	            break;
            }
        } catch (Throwable e) {
        	close();
        	if (session.isConnected()) {
        		session.disconnect();
        	}
            throw new SVNException(e);
        }
        
		myInputStream = new FilterInputStream(myInputStream) {
			public void close() {
            }
		};
		myOutputStream = new FilterOutputStream(myOutputStream) {
			public void close() {
            }
		};
    }

    public void close() throws SVNException {
        if (myChannel != null) {
        	myChannel.disconnect();
        }
        myChannel = null;
        myOutputStream = null;
        myInputStream = null;
    }

    public InputStream getInputStream() throws IOException {
        return myInputStream;
    }

    public OutputStream getOutputStream() throws IOException {
        return myOutputStream;
    }
}