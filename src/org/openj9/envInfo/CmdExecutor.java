/*******************************************************************************
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package org.openj9.envInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.*;

public class CmdExecutor {
    private static CmdExecutor instance;

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    private CmdExecutor() {
    }

    public static CmdExecutor getInstance() {
        if (instance == null) {
            instance = new CmdExecutor();
        }
        return instance;
    }

    public String execute(String[] commands) {
        return execute(commands, DEFAULT_TIMEOUT);
    }

    public String execute(String[] commands, Duration timeout) {
        Process proc = null;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ProcessBuilder builder = new ProcessBuilder(Arrays.asList(commands));
            builder.redirectErrorStream(true);
            proc = builder.start();

            Process finalProc = proc;
            CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
                String rt;
                try {
                    BufferedReader stdOutput = new BufferedReader(new InputStreamReader(finalProc.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    String newline = "";
                    while ((line = stdOutput.readLine()) != null) {
                        sb.append(newline).append(line);
                        newline = "\n";
                    }
                    rt = sb.toString();
                    finalProc.waitFor();
                    return rt;
                } catch (IOException | InterruptedException e) {
                    return "Command could not be executed " + String.join(" ", Arrays.asList(commands));
                }
            }, executor);

            return output.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            String message = "Process failed to exit: " + String.join(" ", Arrays.asList(commands));
            System.err.println(message);
            return message;
        } catch (IOException e) {
            String message = "Process failed to run: " + String.join(" ", Arrays.asList(commands));
            System.err.println(message);
            return message;
        } finally {
            if (proc != null && proc.isAlive()) {
                System.err.println("Forcibly stopping process " + String.join(" ", Arrays.asList(commands)));
                proc.destroyForcibly();
            }
            executor.shutdownNow();
        }
    }
}
