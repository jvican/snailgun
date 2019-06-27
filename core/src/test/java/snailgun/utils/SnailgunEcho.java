/*   

  Copyright 2004-2012, Martian Software, Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

*/

package snailgun.utils;

/**
 * Echos everything it reads from System.in to System.out.
 * 
 * @author <a href="http://www.martiansoftware.com/contact.html">Marty Lamb</a>
 */
public class SnailgunEcho {
	public static void main(String[] args) throws Exception {
		byte[] b = new byte[1024];
		int bytesRead = System.in.read(b);
    boolean exit = false;
		while (!exit && bytesRead != -1) {
      String msg = new String(b, 0, bytesRead);
      if (msg.equals("exit")) {
        exit = true;
      } else {
        System.out.write(b, 0, bytesRead);
        bytesRead = System.in.read(b);
      }
		}
	}
}
