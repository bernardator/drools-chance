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

package org.drools.semantics.builder.model.compilers;

import org.drools.core.metadata.ManyToManyPropertyLiteral;
import org.drools.core.metadata.ManyToOnePropertyLiteral;
import org.drools.core.metadata.OneToManyPropertyLiteral;
import org.drools.core.metadata.OneToOnePropertyLiteral;
import org.drools.core.metadata.ToManyPropertyLiteral;
import org.drools.core.metadata.ToOnePropertyLiteral;
import org.drools.semantics.builder.model.*;
import org.drools.semantics.utils.NameUtils;
import org.w3._2002._07.owl.Thing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MetaclassCompilerImpl extends ModelCompilerImpl implements MetaclassModelCompiler {

    Map<String, Map<String,PropInfo>> propertyCache = new HashMap<String, Map<String, PropInfo>>();

    private static String metaClassTempl = "metaClass";
    private static String metaFactoryTempl = "metaFactory";

    boolean useImplClasses = false;


    @Override
    protected void setModel( OntoModel model ) {
        this.model = (CompiledOntoModel) ModelFactory.newModel( ModelFactory.CompileTarget.METACLASS, model );
    }

    @Override
    public CompiledOntoModel compile( OntoModel model, boolean withMetaclasses ) {
        this.useImplClasses = withMetaclasses;
        preparePropertyCache( model );
        return super.compile( model );
    }

    private void preparePropertyCache( OntoModel model ) {
        for ( Concept con : model.getConcepts() ) {
            if ( con.getIRI().toQuotedString().equals( Thing.IRI ) || con.isAbstrakt() || con.isAnonymous() ) {
                continue;
            }
            Map<String,PropInfo> properties = new HashMap<String, PropInfo>();
            propertyCache.put( con.getFullyQualifiedName(), properties );


            for ( PropertyRelation prop : con.getAvailableProperties() ) {

                PropInfo p = new PropInfo();
                Concept target = findLocalRange( prop.getTarget() );

                p.from = prop;

                p.propName = prop.getName();
                p.typeName = target.getFullyQualifiedName();
                p.simpleTypeName = target.getName();

                p.propIri = prop.getIri();
                p.simple = prop.getMaxCard() != null && prop.getMaxCard() == 1;
                p.primitive = target.isPrimitive();
                p.maxCard = prop.getMaxCard();

                p.inherited = prop.isInherited();

                p.range = NameUtils.map( target.getFullyQualifiedName(), true );
                p.javaRangeType = p.simple ? p.range : List.class.getName() + "<" + p.range + ">";

                p.domain = prop.getDomain().getFullyQualifiedName();

                String inverseName = prop.getInverse() != null ? prop.getInverse().getName() : null;

                if ( prop.isRestricted() ) {
                    if ( ! prop.getTarget().isPrimitive() && ! prop.isTransient() && ! ( prop.isAttribute() && ! ( prop.getMaxCard() != null && prop.getMaxCard() == 1 ) ) ) {
                        properties.put( p.propName, p );
                    }
                } else {
                    if ( prop.isPublic() ) {
                        properties.put( p.propName, p );
                    }
                }


                if ( inverseName != null ) {
                    Map<String, PropInfo> foreignProperties = propertyCache.get( p.range );
                    if ( foreignProperties == null ) {
                        foreignProperties = propertyCache.get( p.range + "Impl" );
                    }
                    if ( foreignProperties != null ) {
                        PropInfo inv = null;
                        if ( foreignProperties.containsKey( inverseName ) ) {
                            inv = foreignProperties.get( inverseName );
                        } else {
                            inverseName = inverseName + p.domain.substring( p.domain.lastIndexOf( "." ) + 1 );
                            inv = foreignProperties.get( inverseName );
                        }
                        if ( inv != null ) {
                            p.inverse = inv;
                            inv.inverse = p;
                        } else {
                            // it may still be a property with same domain and range
                        }
                    }
                }

            }

        }
    }



    @Override
    public void compile( Concept con, Object tgt, Map<String, Object> params ) {

        if ( con.getIRI().toQuotedString().equals( Thing.IRI ) || con.isAbstrakt() || con.isAnonymous() || con.isResolved() ) {
            return;
        }

        Map<String,PropInfo> properties = propertyCache.get( con.getFullyQualifiedName() );
        Set<PropInfo> localProperties = new HashSet<PropInfo>();

        for ( PropertyRelation prop : con.getAvailableProperties() ) {
            PropInfo p = properties.get( prop.getName() );
            if ( isLocal( prop, con ) ) {
                localProperties.add( p );
            }
        }

        for ( PropInfo p : properties.values() ) {
            if ( p.inverse == null ) {
                p.concreteType = p.simple ? ToOnePropertyLiteral.class.getName() : ToManyPropertyLiteral.class.getName();
            } else {
                if ( p.simple ) {
                    p.concreteType = p.inverse.simple ? OneToOnePropertyLiteral.class.getName() : ManyToOnePropertyLiteral.class.getName();
                } else {
                    p.concreteType = p.inverse.simple ? OneToManyPropertyLiteral.class.getName() : ManyToManyPropertyLiteral.class.getName();
                }
            }
        }

        Concept parent = findConcreteParent( con, con.getChosenSuperConcept(), localProperties, properties );


        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put( "klassName", con.getName() );
        map.put( "typeName", con.getName() );
        map.put( "fullTypeName", con.getFullyQualifiedName() );
        map.put( "package", con.getPackage() );
        map.put( "supertypeName", parent.getName() );
        map.put( "supertypePackage", parent.getPackage()  );
        map.put( "typeIri", con.getIri() );
        map.put( "withImpl", this.useImplClasses );

        map.put( "properties", properties.values() );
        map.put( "localProperties", localProperties );
        map.put( "enhancedNames", model.isUseEnhancedNames() );

        String metaClass = SemanticXSDModelCompilerImpl.getTemplatedCode( metaClassTempl, map );

        model.addTrait( con.getFullyQualifiedName(), new AbstractJavaModelImpl.InterfaceHolder( metaClass, con.getPackage() ) );
    }


    private boolean isLocal( PropertyRelation prop, Concept con ) {
        if ( ! prop.isPublic() ) {
            return false;
        }
        if ( con.getChosenProperties().containsValue( prop ) ) {
            return true;
        }
        if ( prop.getDomain().isAnonymous() ) {
            return true;
        }
        return ! con.getChosenSuperConcept().hasImplProperty( prop );
    }

    private Concept findLocalRange( Concept target ) {
        if ( ! ( target.isAnonymous() || target.isAbstrakt() ) ) {
            return target;
        } else {
            return findConcreteParent( target.getChosenSuperConcept() );
        }
    }

    private Concept findConcreteParent( Concept chosenSuperConcept ) {
        while ( chosenSuperConcept.isAbstrakt() || chosenSuperConcept.isAnonymous() ) {
            if ( chosenSuperConcept == chosenSuperConcept.getChosenSuperConcept() ) {
                return model.getConcept( Thing.IRI );
            }
            chosenSuperConcept = chosenSuperConcept.getChosenSuperConcept();
        }
        return chosenSuperConcept;
    }

    private Concept findConcreteParent( Concept base, Concept chosenSuperConcept, Set<PropInfo> localProperties, Map<String, PropInfo> properties ) {
        while ( chosenSuperConcept.isAbstrakt() || chosenSuperConcept.isAnonymous() ) {
            if ( chosenSuperConcept == chosenSuperConcept.getChosenSuperConcept() ) {
                return model.getConcept( Thing.IRI );
            }
            for ( PropertyRelation p : chosenSuperConcept.getChosenProperties().values() ) {
                localProperties.add( properties.get( p.getName() ) );
            }
            chosenSuperConcept = chosenSuperConcept.getChosenSuperConcept();
        }
        return chosenSuperConcept;
    }


    public String buildFactory( String pack ) {
        Map<String, String> classNames = new HashMap<String, String>();
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put( "classNames", classNames );
        map.put( "package", pack );

        for ( Concept con : model.getConcepts() ) {
            if ( !pack.equals( con.getPackage() ) ) {
                continue;
            }
            if ( ! con.isAbstrakt() && model.getTraitNames().contains( con.getFullyQualifiedName() ) ) {
                classNames.put( con.getName(), con.getFullyQualifiedName() );
            }
        }
        return SemanticXSDModelCompilerImpl.getTemplatedCode( metaFactoryTempl, map );
    }



    public static class PropInfo {

        public String propName;
        public String typeName;
        public String javaRangeType;
        public String propIri;
        public boolean simple;
        public String range;
        public String domain;
        public boolean inherited;
        public PropInfo inverse;
        public String concreteType;
        public int position;
        public String simpleTypeName;
        public boolean primitive;
        public Integer maxCard;

        public PropertyRelation from;

        @Override
        public String toString() {
            return "PropInfo{" +
                   "propName='" + propName + '\'' +
                   ", typeName='" + typeName + '\'' +
                   '}';
        }

        @Override
        public boolean equals( Object o ) {
            if ( this == o ) return true;
            if ( !( o instanceof PropInfo ) ) return false;

            PropInfo propInfo = (PropInfo) o;

            if ( !from.equals( propInfo.from ) ) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return from.hashCode();
        }
    }

}
