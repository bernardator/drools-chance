package org.drools.shapes.terms.generator;

import edu.mayo.terms_metamodel.terms.ConceptDescriptor;
import org.apache.commons.io.IOUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.codehaus.plexus.util.StringUtils;
import org.drools.shapes.terms.ConceptCoding;
import org.drools.shapes.terms.vocabulary.AbstractVocabularyCatalog;
import org.drools.shapes.terms.vocabulary.VocabularyCatalog;
import org.mvel2.templates.CompiledTemplate;
import org.mvel2.templates.SimpleTemplateRegistry;
import org.mvel2.templates.TemplateCompiler;
import org.mvel2.templates.TemplateRuntime;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class JavaGenerator {

    private SimpleTemplateRegistry registry;

    protected JavaGenerator() {
        registry = new SimpleTemplateRegistry();
        prepareTemplates();
    }

    private void prepareTemplates() {
        registry.addNamedTemplate( "codefactory-java",
                                   TemplateCompiler.compileTemplate( getResource( "codefactory-java.mvel" ) ) );
        registry.addNamedTemplate( "concepts-java",
                                   TemplateCompiler.compileTemplate( getResource( "concepts-java.mvel" ) ) );
        registry.addNamedTemplate( "vocabularycatalog-java",
                                   TemplateCompiler.compileTemplate( getResource( "vocabularycatalog-java.mvel" ) ) );
    }

    private InputStream getResource( String templ ) {
        return JavaGenerator.class.getResourceAsStream( "/templates/" + templ );
    }


    public void generate( Collection<CodeSystem> codeSystems, String packageName, File outputDir ) {
        outputDir.mkdirs();

        this.generateConcepts( codeSystems, packageName, outputDir );

        this.generateCodeFactory( packageName, outputDir );

        this.generateVocabularyCatalog( packageName, outputDir );
    }

    protected void generateConcepts( Collection<CodeSystem> codeSystems, String packageName, File outputDir ) {
        CompiledTemplate compiled = registry.getNamedTemplate( "concepts-java" );

        for( CodeSystem codeSystem : codeSystems ) {
            if ( ! codeSystem.getConcepts().isEmpty() ) {
                String className = getClassName( codeSystem.getCodeSystemName() );

                Map<String,Object> context = new HashMap<String,Object>();
                context.put( "codeSystem", codeSystem );
                context.put( "className", className );
                context.put( "packageName", packageName );
                context.put( "JavaGenerator", JavaGenerator.class );
                context.put( "implClass", ConceptCoding.class );
                context.put( "typeIntf", ConceptDescriptor.class );

                String mainText = (String) TemplateRuntime.execute( compiled, this, context );
                this.writeToFile( mainText, outputDir, packageName, className );
            }
        }
    }

    protected void generateVocabularyCatalog( String packageName, File outputDir ) {
        CompiledTemplate compiled = registry.getNamedTemplate( "vocabularycatalog-java" );

        Map<String,Object> context = new HashMap<String,Object>();
        context.put( "packageName", packageName );
        context.put( "implClass", AbstractVocabularyCatalog.class );
        context.put( "typeIntf", VocabularyCatalog.class );

        String mainText = (String) TemplateRuntime.execute( compiled, context );
        this.writeToFile( mainText, outputDir, packageName, "VocabularyCatalog" );
    }

    protected void generateCodeFactory( String packageName, File outputDir ) {
        CompiledTemplate compiled = registry.getNamedTemplate( "codefactory-java" );

        Map<String,Object> context = new HashMap<String,Object>();
        context.put( "packageName", packageName );
        context.put( "implClass", ConceptCoding.class );
        context.put( "typeIntf", ConceptDescriptor.class );

        String mainText = (String) TemplateRuntime.execute( compiled, context );
        this.writeToFile( mainText, outputDir, packageName, "CodeFactory" );
    }

    private void writeToFile(
            String content,
            File outputDir,
            String packageName,
            String className) {
        File outputFile = createJavaFile(outputDir, packageName, className);

        FileWriter writer = null;
        try {
            writer = new FileWriter( outputFile );
            writer.write( content );
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            IOUtils.closeQuietly(writer);
        }
    }

    private File createJavaFile(File outputDir, String packageName, String className) {
        File packageDir = new File(outputDir, packageName.replace('.', File.separatorChar));
        packageDir.mkdirs();

        File outputFile = new File(packageDir, className + ".java");
        try {
            if(! outputFile.createNewFile()){
                throw new RuntimeException("Could not create file at: " + outputFile.getAbsolutePath());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outputFile;
    }

    public String getClassName( String codeSystemName ) {
        return StringUtils.capitalise( codeSystemName.replaceAll("[^a-zA-Z0-9]", "_")  );
    }

    public String getPropertyName( String propertyName ) {
        propertyName = fixSpecialCharacters( propertyName );
        return StringUtils.capitalise( propertyName.replaceAll("[^a-zA-Z0-9]", "_")  );
    }

    private static String fixSpecialCharacters( String s ) {
        s = s.replaceAll( ">", "_GT_" );
        s = s.replaceAll( "<", "_LT_" );
        return s;
    }

}
