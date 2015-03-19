package org.opendaylight.ttp.utils;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;
import javassist.ClassPool;
import javax.ws.rs.WebApplicationException;
import org.opendaylight.yangtools.binding.data.codec.gen.impl.StreamWriterGenerator;
import org.opendaylight.yangtools.binding.data.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.yangtools.sal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.yangtools.sal.binding.generator.util.BindingRuntimeContext;
import org.opendaylight.yangtools.sal.binding.generator.util.JavassistUtils;
import org.opendaylight.yangtools.yang.binding.BindingStreamEventWriter;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;
import org.opendaylight.yangtools.yang.binding.util.BindingReflections;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class TTPUtils {

    private SchemaContext context;
    private BindingRuntimeContext bindingContext;
    private BindingNormalizedNodeCodecRegistry codecRegistry;

    public TTPUtils(Iterable<? extends YangModuleInfo> moduleInfos) {
        System.out.println("Building context");
        final ModuleInfoBackedContext moduleContext = ModuleInfoBackedContext.create();
        moduleContext.addModuleInfos(moduleInfos);
        context =  moduleContext.tryToCreateSchemaContext().get();
        System.out.println("Context built");

        System.out.println("Building Binding Context");
        bindingContext = BindingRuntimeContext.create(moduleContext, context);

        System.out.println("Building Binding Codec Factory");
        final BindingNormalizedNodeCodecRegistry bindingStreamCodecs = new BindingNormalizedNodeCodecRegistry(StreamWriterGenerator.create(JavassistUtils.forClassPool(ClassPool.getDefault())));
        bindingStreamCodecs.onBindingRuntimeContextUpdated(bindingContext);
        codecRegistry = bindingStreamCodecs;
        System.out.println("Mapping service built");
        // TODO Auto-generated constructor stub
    }

    public final SchemaContext getSchemaContext() {
        return context;
    }

    /**
     * Converts a {@link DataObject} to a JSON representation in a string using the relevant YANG
     * schema if it is present. This defaults to using a {@link SchemaContextListener} if running an
     * OSGi environment or {@link BindingReflections#loadModuleInfos()} if run while not in an OSGi
     * environment or if the schema isn't available via {@link SchemaContextListener}.
     *
     * @param object
     * @return
     * @throws WebApplicationException
     * @throws IOException
     */
    public final String jsonStringFromDataObject(InstanceIdentifier<?> path, DataObject object) {
            final SchemaPath scPath = SchemaPath.create(FluentIterable.from(path.getPathArguments()).transform(new Function<PathArgument, QName>() {

                @Override
                public QName apply(final PathArgument input) {
                    return BindingReflections.findQName(input.getType());
                }

            }), true);

            final Writer writer = new StringWriter();
            final NormalizedNodeStreamWriter domWriter = JSONNormalizedNodeStreamWriter.create(context, scPath.getParent(), scPath.getLastComponent().getNamespace(), writer);
            final BindingStreamEventWriter bindingWriter = codecRegistry.newWriter(path, domWriter);

            try {
                codecRegistry.getSerializer(path.getTargetType()).serialize(object, bindingWriter);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            return writer.toString();
    }

    public static final Set<DataSchemaNode> getAllTheNode(SchemaContext context) {
        Set<DataSchemaNode> nodes = new HashSet<DataSchemaNode>();
        getAllTheNodesHelper(context, nodes);
        return nodes;
    }

    private static final void getAllTheNodesHelper(DataNodeContainer dcn, Set<DataSchemaNode> nodes) {
        for (DataSchemaNode dsn : dcn.getChildNodes()) {
            if (dsn instanceof DataNodeContainer) {
                getAllTheNodesHelper((DataNodeContainer) dsn, nodes);
            }
            nodes.add(dsn);
        }
    }

    /**
     * DON'T CALL THIS IN PRODUCTION CODE EVER!!! UNTIL IT IS FIXED!
     * <p/>
     * Return the {@link DataSchemaNode}
     *
     * @param context
     * @param d
     * @deprecated
     */
    @Deprecated
    public static final DataSchemaNode getSchemaNodeForDataObject(SchemaContext context,
            DataObject d) {
        QName qn = BindingReflections.findQName(d.getClass());

        Set<DataSchemaNode> allTheNodes = getAllTheNode(context);

        // TODO: create a map to make this faster!!!!
        for (DataSchemaNode dsn : allTheNodes) {
            if (dsn instanceof DataNodeContainer) {
                allTheNodes.addAll(((DataNodeContainer) dsn).getChildNodes());
            }
            if (dsn.getQName().equals(qn)) {
                return dsn;
            }
        }
        return null;
    }

}
