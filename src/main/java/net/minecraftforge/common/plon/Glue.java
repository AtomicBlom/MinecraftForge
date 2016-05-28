package net.minecraftforge.common.plon;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.animation.Event;
import net.minecraftforge.common.animation.ITimeValue;
import net.minecraftforge.common.animation.TimeValues;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.animation.AnimationStateMachineBase;
import net.minecraftforge.common.model.animation.Clips;
import net.minecraftforge.common.model.animation.IAnimationStateMachine;
import net.minecraftforge.common.model.animation.IClip;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Created by rainwarrior on 5/28/16.
 */

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import static net.minecraftforge.common.plon.AST.*;
public enum Glue implements IResourceManagerReloadListener
{
    INSTANCE;

    public static ISExp getLoadOp()
    {
        return GlueOp.Load;
    }

    public static ISExp getModelClip()
    {
        return GlueOp.ModelClip;
    }

    public static ISExp getTriggerPositive()
    {
        return GlueOp.TriggerPositive;
    }

    public static ISExp getUserOp(final Function<? super String, Optional<? extends ITimeValue>> userParameters)
    {
        return new ICallableAtom()
        {
            @Override
            public ISExp apply(IList args)
            {
                if (Interpreter.length(args) != 1)
                {
                    throw new IllegalArgumentException("user needs 1 argument, got: " + args);
                }
                Cons c2 = (Cons) args;
                if (c2.car instanceof StringAtom)
                {
                    String parameterName = ((StringAtom) c2.car).value;
                    Optional<? extends ITimeValue> parameter = userParameters.apply(parameterName);
                    if (parameter == null || !parameter.isPresent())
                    {
                        throw new IllegalArgumentException("Unknown user parameter: " + parameterName);
                    }
                    return new User(parameterName, parameter.get());
                }
                throw new IllegalArgumentException("user needs string argument, got: " + args);
            }

            @Override
            public String toString()
            {
                return "&user";
            }
        };
    }

    private static IResourceManager manager;
    private static final ResourceLocation rootLibraryLocation = new ResourceLocation("forge", "root_library_unstable");
    private static Map rootLibrary;
    private static final Interpreter in = new Interpreter();

    private static class User implements ICallableAtom
    {
        private final String name;
        private final ITimeValue parameter;

        User(String name, ITimeValue parameter)
        {
            this.name = name;
            this.parameter = parameter;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            User user = (User) o;
            return Objects.equal(parameter, user.parameter);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(parameter);
        }

        @Override
        public String toString()
        {
            return "&user[" + name + "]";
        }

        public ISExp apply(IList args)
        {
            if (Interpreter.length(args) != 1)
            {
                throw new IllegalArgumentException("User parameter \"" + name + "\" needs 1 argument, got " + args);
            }
            Cons cons = (Cons) args;
            if (cons.car instanceof FloatAtom)
            {
                return makeFloat(parameter.apply(((FloatAtom) cons.car).value));
            }
            throw new IllegalArgumentException("User parameter \"" + name + "\" needs float argument, got " + cons.car);
        }
    }

    private static class ClipValue implements IAtom
    {
        private final IClip clip;
        private final float time;

        ClipValue(IClip clip, float time)
        {
            this.clip = clip;
            this.time = time;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClipValue other = (ClipValue) o;
            return Objects.equal(clip, other.clip) && time == other.time;
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(clip, time);
        }

        @Override
        public String toString()
        {
            return "&clip[" + clip + ", " + time + "]";
        }
    }

    private static ResourceLocation getPlonLocation(ResourceLocation loc)
    {
        return new ResourceLocation(loc.getResourceDomain(), "plon/" + loc.getResourcePath() + ".json");
    }

    private enum GlueOp implements ICallableAtom
    {
        Load("load")
        {
            @Override
            public ISExp apply(IList args)
            {
                if (Interpreter.length(args) != 1)
                {
                    throw new IllegalArgumentException("&load needs 1 argument, got " + args);
                }
                Cons cons = (Cons) args;
                if (cons.car instanceof StringAtom)
                {
                    String location = ((StringAtom) cons.car).value;
                    ResourceLocation plonLocation = new ResourceLocation(location);
                    try
                    {
                        ISExp exp = read(plonLocation);
                        if(!(exp instanceof Map))
                        {
                            throw new IllegalArgumentException("Loaded file " + plonLocation + " is not a map: " + exp);
                        }
                        Map map = (Map) exp;
                        return in.loadFrame(map);
                    }
                    catch(IOException e)
                    {
                        throw new IllegalArgumentException("Couldn't load file " + plonLocation, e);
                    }
                }
                throw new IllegalArgumentException("&load needs string argument, got " + cons.car);
            }
        },
        ModelClip("model_clip")
        {
            @Override
            public ISExp apply(IList args)
            {
                if (Interpreter.length(args) == 2)
                {
                    Cons c1 = (Cons) args;
                    Cons c2 = (Cons) c1.cdr;
                    if (c1.car instanceof StringAtom)
                    {
                        String modelClipName = ((StringAtom) c1.car).value;
                        if (c2.car instanceof FloatAtom)
                        {
                            float time = ((FloatAtom) c2.car).value;
                            int at = modelClipName.lastIndexOf('@');
                            String location = modelClipName.substring(0, at);
                            String clipName = modelClipName.substring(at + 1, modelClipName.length());
                            ResourceLocation model;
                            if (location.indexOf('#') != -1)
                            {
                                model = new ModelResourceLocation(location);
                            }
                            else
                            {
                                model = new ResourceLocation(location);
                            }
                            // TODO: cache?
                            return new ClipValue(Clips.getModelClipNode(model, clipName), time);
                        }
                    }
                }
                throw new IllegalArgumentException("&model_clip needs a string and a float argument, got " + args);
            }
        },
        TriggerPositive("trigger_positive")
        {
            @Override
            public ISExp apply(IList args)
            {
                if (Interpreter.length(args) == 3)
                {
                    Cons c1 = (Cons) args;
                    Cons c2 = (Cons) c1.cdr;
                    Cons c3 = (Cons) c2.cdr;
                    if (c1.car instanceof ClipValue)
                    {
                        ClipValue clip = (ClipValue) c1.car;
                        if (c2.car instanceof FloatAtom)
                        {
                            float trigger = ((FloatAtom) c2.car).value;
                            if(c3.car instanceof StringAtom)
                            {
                                String event = ((StringAtom) c3.car).value;
                                // FIXME
                                return new ClipValue(new Clips.TriggerClip(clip.clip, new TimeValues.ConstValue(-1), event), clip.time);
                            }
                        }
                    }
                }
                throw new IllegalArgumentException("&trigger_positive needs a clip, a float and a string arguments, got " + args);
            }
        };

        private static final ImmutableMap<ISExp, GlueOp> values;

        static
        {
            ImmutableMap.Builder<ISExp, GlueOp> builder = ImmutableMap.builder();
            for (GlueOp op : values())
            {
                builder.put(op.symbol, op);
            }
            values = builder.build();
        }

        private final ISExp symbol;
        private final String name;

        GlueOp(String name)
        {
            this.name = name;
            this.symbol = makeSymbol(name);
        }

        @Override
        public String toString()
        {
            return "&" + name;
        }
    }

    public static IAnimationStateMachine loadASM(ResourceLocation location, ImmutableMap<String, ITimeValue> customParameters) throws IOException
    {
        ISExp source = read(location);
        return PlonAnimationStateMachine.create(source, customParameters);
    }

    public static ISExp read(ResourceLocation location) throws IOException
    {
        ResourceLocation fileLocation = getPlonLocation(location);
        Reader reader;
        if(manager == null && (Launch.blackboard == null || (Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment")))
        {
            if(location == rootLibraryLocation)
            {
                reader = new FileReader("assets/" + location.getResourceDomain() + "/plon/" + location.getResourcePath() + ".json");
            }
            else
            {
                reader = new FileReader(location.getResourcePath() + ".json");
            }
        }
        else
        {
            IResource resource = manager.getResource(fileLocation);
            reader = new InputStreamReader(resource.getInputStream(), Charsets.UTF_8);
        }
        // TODO cache
        return sexpGson.fromJson(reader, ISExp.class);
    }

    private static final Gson sexpGson = new GsonBuilder().registerTypeAdapterFactory(AST.SExpTypeAdapterFactory.INSTANCE).create();

    private static final ISExp statesKey = AST.makeString("states");
    private static final ISExp transitionsKey = AST.makeString("transitions");
    private static final ISExp startStateKey = AST.makeString("start_state");

    private static final class PlonAnimationStateMachine extends AnimationStateMachineBase
    {
        private final ImmutableMap<ISExp, ISExp> rootFrame;
        private final ISExp asmSource;

        private PlonAnimationStateMachine(ImmutableMap<ISExp, ISExp> rootFrame, ISExp asmSource, final ImmutableMap<String, ITimeValue> customParameters, ImmutableList<String> states, ImmutableMultimap<String, String> transitions, String startState)
        {
            super(customParameters, states, transitions, startState);
            this.asmSource = asmSource;
            this.rootFrame = rootFrame;
            initialize();
        }

        private static ImmutableMap<ISExp, ISExp> makeRootFrame(final ImmutableMap<String, ITimeValue> customParameters)
        {
            if(rootLibrary == null)
            {
                loadRootLibrary();
            }
            ISExp userResolver = getUserOp(new Function<String, Optional<? extends ITimeValue>>()
            {
                @Override
                public Optional<? extends ITimeValue> apply(String name)
                {
                    if (customParameters.containsKey(name))
                    {
                        return Optional.of(customParameters.get(name));
                    }
                    return Optional.absent();
                }
            });
            java.util.Map<ISExp, ISExp> rootFrame = Maps.newHashMap();
            rootFrame.putAll(GlueOp.values);
            rootFrame.put(AST.makeSymbol("user"), userResolver);
            rootFrame.putAll(rootLibrary.value);
            return ImmutableMap.copyOf(rootFrame);
        }

        private static PlonAnimationStateMachine create(ISExp asmSource, final ImmutableMap<String, ITimeValue> customParameters)
        {
            ImmutableMap<ISExp, ISExp> rootFrame = makeRootFrame(customParameters);
            ISExp asmDef = in.eval(new Cons(makeSymbol("asm_def"), new Cons(asmSource, Nil.INSTANCE)), rootFrame);
            // FIXME make exception strings less silly
            if(!(asmDef instanceof Map))
            {
                throw new IllegalArgumentException("asm_def applied to asm loaded from plon needs to eval to a map");
            }
            Map asm = (Map) asmDef;
            if(!asm.value.containsKey(statesKey))
            {
                throw new IllegalArgumentException("asm_def applied to asm loaded from plon needs to eval to a map with a \"states\" key");
            }
            if(!asm.value.containsKey(transitionsKey))
            {
                throw new IllegalArgumentException("asm_def applied to asm loaded from plon needs to eval to a map with a \"transitions\" key");
            }
            if(!asm.value.containsKey(startStateKey))
            {
                throw new IllegalArgumentException("asm_def applied to asm loaded from plon needs to eval to a map with a \"start_state\" key");
            }
            ISExp exp = asm.value.get(statesKey);
            if(!(exp instanceof Cons))
            {
                throw new IllegalArgumentException("asm_def applied to asm loaded from plon needs to eval to a map with a \"states\" key with a list value, got: " + exp);
            }
            Cons cons = (Cons) exp;
            ImmutableList.Builder<String> stateBuilder = ImmutableList.builder();
            while(cons.cdr != Nil.INSTANCE)
            {
                ISExp state = cons.car;
                if(!(state instanceof StringAtom))
                {
                    throw new IllegalArgumentException("asm_def applied to asm loaded from plon needs to eval to a map with a \"states\" key with a list value, got: " + state);
                }
                stateBuilder.add(((StringAtom) state).value);
                cons = (Cons) cons.cdr;
            }
            exp = asm.value.get(transitionsKey);
            if(!(exp instanceof IMap))
            {
                throw new IllegalArgumentException("asm_def applied to asm loaded from plon needs to eval to a map with a \"transitions\" key with a map value, got: " + exp);
            }
            ImmutableMultimap<String, String> transitions;
            if(exp == MNil.INSTANCE)
            {
                transitions = ImmutableMultimap.of();
            }
            else
            {
                Map map = (Map) exp;
                ImmutableMultimap.Builder<String, String> builder = ImmutableMultimap.builder();
                for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
                {
                    if(!(entry.getKey() instanceof StringAtom))
                    {
                        throw new IllegalArgumentException("transition multimap can only contain string keys, got: " + entry.getKey());
                    }
                    String key = ((StringAtom) entry.getKey()).value;
                    if(entry.getValue() instanceof StringAtom)
                    {
                        builder.put(key, ((StringAtom) entry.getValue()).value);
                    }
                    else if(entry.getValue() instanceof IList)
                    {
                        IList tos = (IList) entry.getValue();
                        while(tos != Nil.INSTANCE)
                        {
                            ISExp to = ((Cons) tos).car;
                            if(!(to instanceof StringAtom))
                            {
                                throw new IllegalArgumentException("transition multimap can only contain string values, got: " + to);
                            }
                            builder.put(key, ((StringAtom) to).value);
                            tos = ((Cons) tos).cdr;
                        }
                    }
                    else
                    {
                        throw new IllegalArgumentException("transition multimap can only contain string values, got: " + entry.getValue());
                    }
                }
                transitions = builder.build();
            }

            exp = asm.value.get(startStateKey);
            if(!(exp instanceof StringAtom))
            {
                throw new IllegalArgumentException("asm_def applied to asm loaded from plon needs to eval to a map with a \"start_state\" key with a string value, got: " + exp);
            }
            return new PlonAnimationStateMachine(rootFrame, asmSource, customParameters, stateBuilder.build(), transitions, ((StringAtom) exp).value);
        }

        private ClipValue applyAsm(String state, float time)
        {
            ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
            builder.putAll(rootFrame);
            builder.put(makeSymbol("time"), makeFloat(time));
            ISExp clip = in.eval(new Cons(makeSymbol("asm_run"), new Cons(asmSource, new Cons(makeString(state), Nil.INSTANCE))), builder.build());
            if(!(clip instanceof ClipValue))
            {
                throw new IllegalArgumentException("asm_run applied to asm loaded from plon needs to eval to a clip, got: " + clip);
            }
            return (ClipValue) clip;
        }

        @Override
        public Pair<IModelState, Iterable<Event>> apply(float time)
        {
            ClipValue clip = applyAsm(currentState(), time);
            if(lastPollTime == Float.NEGATIVE_INFINITY)
            {
                lastPollTime = clip.time;
            }
            Pair<IModelState, Iterable<Event>> pair = Clips.apply(clip.clip, lastPollTime, clip.time);
            lastPollTime = clip.time;
            return filterSpecialEvents(pair);
        }
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager)
    {
        Glue.manager = manager;
    }

    private static void loadRootLibrary()
    {
        ISExp exp;
        try
        {
            exp = read(rootLibraryLocation);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Exception reading root plon library", e);
        }
        if(!(exp instanceof Map))
        {
            throw new IllegalStateException("Root plon library isn't a map");
        }
        Map map = (Map) exp;
        map = in.loadFrame(map);
        ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
        builder.putAll(GlueOp.values);
        builder.putAll(map.value);
        rootLibrary = new Map(builder.build());
    }
}
