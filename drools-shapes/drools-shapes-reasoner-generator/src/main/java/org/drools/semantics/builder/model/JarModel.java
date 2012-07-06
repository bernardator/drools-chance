/*
 * Copyright 2011 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.semantics.builder.model;

import java.io.ByteArrayOutputStream;
import java.util.Map;

public interface JarModel extends JavaInterfaceModel {


    public byte[] getCompiledTrait( String name );
    
    public Map<String, Holder> getCompiledTraits();

    public void addCompiledTrait( String name, Holder compiled );

    public ByteArrayOutputStream buildJar( );


    public static class Holder {
        private byte[] bytes;

        public byte[] getBytes() {
            return bytes;
        }

        public Holder(byte[] bytes) {
            this.bytes = bytes;
        }
    }


}
